package no.ntnu.tollefsen.fant.services;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.ws.rs.*;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.FormDataParam;

import lombok.extern.java.Log;
import net.coobird.thumbnailator.Thumbnails;
import no.ntnu.tollefsen.fant.domain.User;
import no.ntnu.tollefsen.fant.domain.Group;
import no.ntnu.tollefsen.fant.domain.Photo;
import no.ntnu.tollefsen.fant.domain.Sellable;

import static no.ntnu.tollefsen.fant.domain.Sellable.SELLABLE_FIND_FOR_SALE;


/**
 *
 * @author mikael
 */
@Log
@Path("fant")
@Stateless
public class FantService {
    @PersistenceContext
    EntityManager em;

    @Inject
    @ConfigProperty(name = "photo.storage.path", defaultValue = "fantphotos")
    String photoPath;

    @Inject
    JsonWebToken principal;
    
    /**
     * Get all sellables not already sold
     * 
     * @return 
     */
    @GET
    public List<Sellable> getSellables() {
        return em.createNamedQuery(SELLABLE_FIND_FOR_SALE, Sellable.class).getResultList();
    }

    /**
     * A registered user may buy a sellable
     * 
     * @param id
     * @return
     */
    @PUT
    @Path("buy/{id}")
    @RolesAllowed(Group.USER)
    public Response buy(@PathParam("id") @DefaultValue("-1") Long id) {
        Sellable result = em.find(Sellable.class, id);
        
        if(result != null) {
            User buyer = em.find(User.class, principal.getName());
            result.setBuyer(buyer);
        }

        return result != null ? Response.ok(result).build() :
                Response.status(Response.Status.NOT_FOUND).build();
    }


    /**
     * Remove Sellable and associated Photos. 
     * 
     * @param id
     * @return
     */
    @DELETE
    @RolesAllowed(Group.USER)
    public Response delete(@QueryParam("id") @DefaultValue("-1") Long id) {
        Sellable result = em.find(Sellable.class, id);
        if(result != null) {
            em.remove(result);
            return Response.ok(result).build();
        }
        
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    
    /**
     * Get path for photos
     *
     * @return
     */
    protected java.nio.file.Path getPhotoPath() {
        java.nio.file.Path result = Paths.get(photoPath);
        if(!Files.isDirectory(result)) {
            try {
                Files.createDirectories(result);
            } catch (IOException e) {
                String message = String.format("Failed to create directory %s for photos",result.toString());
                log.log(Level.SEVERE, message, e);
                throw new RuntimeException(message);
            }
        }

        return result;
    }

    
    /**
     * Upload photo to Fant
     * 
     * @param title the title of Sellable
     * @param description the description of Sellable
     * @param price the price of Sellable
     * @param multiPart one or more photos associated with Sellable
     * @return
     */
    @POST
    @Path("create")
    @Produces({MediaType.APPLICATION_JSON})
    @Consumes({MediaType.MULTIPART_FORM_DATA})
    @RolesAllowed(Group.USER)
    public Response uploadFromURIs(
            @FormDataParam("title")
            String title,
            @FormDataParam("description")
            String description,            
            @FormDataParam("price")
            BigDecimal price,            
            FormDataMultiPart multiPart) {
        List<Photo> photos = new ArrayList<>();
        List<FormDataBodyPart> images = multiPart.getFields("files");
        if(images != null) {
            try {
                for(FormDataBodyPart part : images) {
                    InputStream is = part.getEntityAs(InputStream.class);
                    UUID pid = UUID.randomUUID();
                    Files.copy(is, getPhotoPath().resolve(pid.toString()));
                    photos.add(new Photo(pid.toString()));
                }
            } catch (IOException ex) {
                log.log(Level.SEVERE, "Failed to store photo", ex);
                return Response.serverError().build();
            }
        }
        
        User seller = em.find(User.class, principal.getName());
        Sellable result = new Sellable(title, description, price, seller);
        result.setPhotos(photos);
        em.persist(result);
        return Response.ok(result).build();
    }   
    
    
    
    /**
     * Streams an image to the browser(the actual compressed pixels). The image
     * will be scaled to the appropriate with if the with parameter is provided.
     *
     * @param name the filename of the image
     * @param width the required scaled with of the image
     * 
     * @return the image in original format or in jpeg if scaled
     */
    @GET
    @Path("photo/{name}")
    @Produces("image/jpeg")
    public Response getPhoto(@PathParam("name") String name,
                             @QueryParam("width") int width) {
        java.nio.file.Path image = getPhotoPath().resolve(name);
        if(Files.exists(image)) {
            StreamingOutput result = (OutputStream os) -> {
                
                if(width == 0) {
                    Files.copy(image, os);
                    os.flush();
                } else {
                    Thumbnails.of(image.toFile())
                              .size(width, width)
                              .outputFormat("jpeg")
                              .toOutputStream(os);
                }
            };

            // Ask the browser to cache the image for 24 hours
            CacheControl cc = new CacheControl();
            cc.setMaxAge(86400);
            cc.setPrivate(true);

            return Response.ok(result).cacheControl(cc).build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }     
}