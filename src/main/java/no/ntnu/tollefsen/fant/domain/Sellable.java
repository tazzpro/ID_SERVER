package no.ntnu.tollefsen.fant.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

import static no.ntnu.tollefsen.fant.domain.Sellable.SELLABLE_FIND_FOR_SALE;

/**
 *
 * @author mikael
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@NamedQuery(name= SELLABLE_FIND_FOR_SALE,
            query="SELECT s FROM Sellable s WHERE s.buyer IS NULL ORDER BY s.created DESC")
public class Sellable implements Serializable {
    public static final String SELLABLE_FIND_FOR_SALE = "Sellable.findForSale";

    @Id
    @GeneratedValue
    Long id;
    String title;
    String description;
    BigDecimal price;
    
    @OneToMany(cascade = CascadeType.ALL)
    List<Photo> photos;
    
    @ManyToOne
    User seller;
    
    @ManyToOne
    User buyer;

    @Version
    Timestamp version;

    @Temporal(javax.persistence.TemporalType.DATE)
    Date created;

    public Sellable(String title, String description, BigDecimal price, User seller) {
        this.title = title;
        this.description = description;
        this.price = price;
        this.seller = seller;
    }

    @PrePersist
    protected void onCreate() {
        created = new Date();
    }
}
