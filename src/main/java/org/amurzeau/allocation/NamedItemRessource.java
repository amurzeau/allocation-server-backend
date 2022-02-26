package org.amurzeau.allocation;

import java.util.List;

import javax.persistence.PersistenceException;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.amurzeau.allocation.rest.ErrorReply;
import org.amurzeau.allocation.rest.ErrorType;
import org.amurzeau.allocation.rest.NamedItem;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.resteasy.reactive.RestResponse.Status;

import io.smallrye.mutiny.Uni;

public class NamedItemRessource<T extends NamedItem> {
    private static final Logger LOG = Logger.getLogger(NamedItemRessource.class);
    private final Class<T> typeParameterClass;

    NamedItemRessource(Class<T> typeParameterClass) {
        this.typeParameterClass = typeParameterClass;
    }

    @GET
    public Uni<List<T>> getAll(@RestQuery Boolean deleted) {
        return NamedItem.getAll(typeParameterClass, deleted);
    }

    @GET
    @Path("{id}")
    public Uni<Response> getById(String id) {
        return NamedItem.getById(typeParameterClass, id).onItem().transform(item -> {
            if (item != null) {
                return Response.ok(item).build();
            } else {
                return Response
                        .status(Status.NOT_FOUND)
                        .entity(ErrorReply.create(ErrorType.NOT_EXISTS, "No item with id %s", id))
                        .build();
            }
        });
    }

    @POST
    public Uni<Response> postNew(T value, UriInfo uriInfo) {
        if (value.id == null) {
            LOG.errorv("id is required");
            return Uni.createFrom()
                    .item(Response.status(Status.NOT_ACCEPTABLE)
                            .entity(ErrorReply.create(ErrorType.MISSING_REQUIRED_FIELD, "id field is required"))
                            .build());
        }

        if (!value.id.matches("[a-z0-9\\-]+")) {
            LOG.errorv("id has invalid characters, only alpha and dash is allowed: {0}", value.id);
            return Uni.createFrom().item(Response.status(Status.NOT_ACCEPTABLE)
                    .entity(ErrorReply.create(ErrorType.INVALID_FIELD, "id field must contains a-z, 0-9 or -"))
                    .build());
        }

        return NamedItem.postCreate(value).onItem().transform(res -> {
            if (res) {
                return Response.ok(value).build();
            } else {
                return Response.status(Status.CONFLICT).build();
            }
        });
    }

    @PUT
    @Path("{id}")
    public Uni<Response> putUpdate(String id, T value) {
        if (!id.matches("[a-z0-9\\-]+")) {
            LOG.errorv("id has invalid characters, only alpha and dash is allowed: {0}", id);
            return Uni.createFrom().item(Response.status(Status.NOT_ACCEPTABLE)
                    .entity(ErrorReply.create(ErrorType.INVALID_FIELD, "id field must contains a-z, 0-9 or -"))
                    .build());
        }

        if (value.id == null) {
            LOG.errorv("id within object is null", id);
            return Uni.createFrom().item(Response.status(Status.NOT_ACCEPTABLE)
                    .entity(ErrorReply.create(ErrorType.INVALID_FIELD, "id must not be null"))
                    .build());
        }

        if (!id.equals(value.id)) {
            LOG.errorv("id from URL is not the same as id within object", id);
            return Uni.createFrom().item(Response.status(Status.NOT_ACCEPTABLE)
                    .entity(ErrorReply.create(ErrorType.INVALID_FIELD, "id field is not the same as in the URL"))
                    .build());
        }

        return NamedItem.putUpdate(typeParameterClass, id, value).onItem().transform(res -> {
            return Response.ok(res).build();
        });
    }

    @DELETE
    @Path("{id}")
    public Uni<Response> delete(String id) {
        return NamedItem.delete(typeParameterClass, id)
                .onItem().transform(res -> {
                    if (res) {
                        return Response.ok().build();
                    } else {
                        return Response.status(Status.NOT_FOUND)
                                .entity(ErrorReply.create(ErrorType.NOT_EXISTS, "No item with id %s", id))
                                .build();
                    }
                })
                .onFailure(PersistenceException.class).recoverWithItem(res -> {
                    return Response.status(Status.NOT_ACCEPTABLE)
                            .entity(ErrorReply.create(ErrorType.CANT_DELETE_REFERENCED,
                                    "Item id %s is referenced and can't be deleted",
                                    id))
                            .build();
                });
    }
}
