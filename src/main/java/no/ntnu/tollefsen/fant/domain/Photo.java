package no.ntnu.tollefsen.fant.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Date;

/**
 *
 * @author mikael
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Photo implements Serializable {
    @Id
    @GeneratedValue
    Long id;
    
    @Column(nullable = false)
    String subpath;

    @Version
    Timestamp version;

    @Temporal(javax.persistence.TemporalType.DATE)
    Date created;

    public Photo(String subpath) {
        this.subpath = subpath;
    }

    @PrePersist
    protected void onCreate() {
        created = new Date();
    }
}
