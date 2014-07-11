package com.siemens.cto.aem.ws.rest.v1.service.jvm;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.siemens.cto.aem.domain.model.id.Identifier;
import com.siemens.cto.aem.domain.model.jvm.Jvm;
import com.siemens.cto.aem.ws.rest.v1.provider.JvmIdsParameterProvider;
import com.siemens.cto.aem.ws.rest.v1.provider.PaginationParamProvider;
import com.siemens.cto.aem.ws.rest.v1.provider.TimeoutParameterProvider;
import com.siemens.cto.aem.ws.rest.v1.service.jvm.impl.JsonControlJvm;
import com.siemens.cto.aem.ws.rest.v1.service.jvm.impl.JsonCreateJvm;
import com.siemens.cto.aem.ws.rest.v1.service.jvm.impl.JsonUpdateJvm;

@Path("/jvms")
@Produces(MediaType.APPLICATION_JSON)
public interface JvmServiceRest {

    @GET
    Response getJvms(@BeanParam final PaginationParamProvider paginationParamProvider);

    @GET
    @Path("/{jvmId}")
    Response getJvm(@PathParam("jvmId") final Identifier<Jvm> aJvmId);

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    Response createJvm(final JsonCreateJvm aJvmToCreate);

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    Response updateJvm(final JsonUpdateJvm aJvmToUpdate);

    @DELETE
    @Path("/{jvmId}")
    Response removeJvm(@PathParam("jvmId") final Identifier<Jvm> aJvmId);

    @POST
    @Path("/{jvmId}/commands")
    Response controlJvm(@PathParam("jvmId") final Identifier<Jvm> aJvmId,
                        final JsonControlJvm aJvmToControl);

    @GET
    @Path("/states")
    Response pollJvmStates(@Context final HttpServletRequest aRequest,
                           @BeanParam final TimeoutParameterProvider aTimeoutParamProvider,
                           @QueryParam("clientId") @DefaultValue("1") final String clientId);

    @GET
    @Path("/states/current")
    //TODO This should be reconciled with pagination, and with how to retrieve the states for every jvm without having to explicitly specify them
    Response getCurrentJvmStates(@BeanParam final JvmIdsParameterProvider jvmIdsParameterProvider);
}
