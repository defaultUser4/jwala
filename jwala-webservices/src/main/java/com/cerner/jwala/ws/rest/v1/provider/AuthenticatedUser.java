package com.cerner.jwala.ws.rest.v1.provider;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;

import com.cerner.jwala.common.domain.model.user.User;

public class AuthenticatedUser {

    @Context
    private SecurityContext context;

    public AuthenticatedUser() {
    }

    public AuthenticatedUser(final SecurityContext theSecurityContext) {
        context = theSecurityContext;
    }

    public User getUser() {
        if(context.getUserPrincipal() == null) { return new User("nouser"); } // do not check in
        //TODO This should throw some sort of security exception if there's nobody logged in
        return new User(context.getUserPrincipal().getName());
    }
}