/*
 * This file is part of the Soapbox Race World core source code.
 * If you use any of this code for third-party purposes, please provide attribution.
 * Copyright (c) 2020.
 */

package com.soapboxrace.core.api;

import com.soapboxrace.core.api.util.Secured;
import com.soapboxrace.core.bo.*;
import com.soapboxrace.core.jpa.EventSessionEntity;
import com.soapboxrace.jaxb.http.LobbyInfo;
import com.soapboxrace.jaxb.http.OwnedCarTrans;
import com.soapboxrace.jaxb.http.SecurityChallenge;
import com.soapboxrace.jaxb.http.SessionInfo;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import com.soapboxrace.core.bo.util.DiscordWebhook;
import com.soapboxrace.core.jpa.PersonaEntity;
import com.soapboxrace.core.jpa.EventEntity;
import com.soapboxrace.core.jpa.LobbyEntity;
import com.soapboxrace.core.dao.PersonaDAO;
import com.soapboxrace.core.dao.LobbyDAO;

@Path("/matchmaking")
public class MatchMaking {

    @EJB
    private EventBO eventBO;

    @EJB
    private LobbyBO lobbyBO;

    @EJB
    private TokenSessionBO tokenSessionBO;

    @EJB
    private PersonaBO personaBO;

    @EJB
    private MatchmakingBO matchmakingBO;

	@EJB
    private DiscordWebhook discord;
    
    @EJB
    private ParameterBO parameterBO;
    
    @EJB
    private PersonaDAO personaDAO;

    @EJB
    private LobbyDAO lobbyDAO;

    @PUT
    @Secured
    @Path("/joinqueueracenow")
    @Produces(MediaType.APPLICATION_XML)
    public String joinQueueRaceNow(@HeaderParam("securityToken") String securityToken) {
        Long activePersonaId = tokenSessionBO.getActivePersonaId(securityToken);
        OwnedCarTrans defaultCar = personaBO.getDefaultCar(activePersonaId);
        lobbyBO.joinFastLobby(activePersonaId, defaultCar.getCustomCar().getCarClassHash());
        return "";
    }

    @PUT
    @Secured
    @Path("/joinqueueevent/{eventId}")
    @Produces(MediaType.APPLICATION_XML)
    public String joinQueueEvent(@HeaderParam("securityToken") String securityToken,
                                 @PathParam("eventId") int eventId) {
        Long activePersonaId = tokenSessionBO.getActivePersonaId(securityToken);
        lobbyBO.joinQueueEvent(activePersonaId, eventId);
        return "";
    }

    @PUT
    @Secured
    @Path("/leavequeue")
    @Produces(MediaType.APPLICATION_XML)
    public String leaveQueue(@HeaderParam("securityToken") String securityToken) {
        matchmakingBO.removePlayerFromQueue(tokenSessionBO.getActivePersonaId(securityToken));
        return "";
    }

    @PUT
    @Secured
    @Path("/leavelobby")
    @Produces(MediaType.APPLICATION_XML)
    public String leavelobby(@HeaderParam("securityToken") String securityToken) {
        Long activePersonaId = tokenSessionBO.getActivePersonaId(securityToken);
        Long activeLobbyId = tokenSessionBO.getActiveLobbyId(securityToken);
        if (activeLobbyId != null && !activeLobbyId.equals(0L)) {
            lobbyBO.removeEntrantFromLobby(activePersonaId, activeLobbyId);
        }
        return "";
    }

    @GET
    @Secured
    @Path("/launchevent/{eventId}")
    @Produces(MediaType.APPLICATION_XML)
    public SessionInfo launchEvent(@HeaderParam("securityToken") String securityToken,
                                   @PathParam("eventId") int eventId) {
        SessionInfo sessionInfo = new SessionInfo();
        SecurityChallenge securityChallenge = new SecurityChallenge();
        securityChallenge.setChallengeId("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        securityChallenge.setLeftSize(14);
        securityChallenge.setPattern("FFFFFFFFFFFFFFFF");
        securityChallenge.setRightSize(50);
        sessionInfo.setChallenge(securityChallenge);
        sessionInfo.setEventId(eventId);
        EventSessionEntity createEventSession = eventBO.createEventSession(securityToken, eventId);
        sessionInfo.setSessionId(createEventSession.getId());
        tokenSessionBO.setActiveLobbyId(securityToken, 0L);
        return sessionInfo;
    }

    @PUT
    @Secured
    @Path("/makeprivatelobby/{eventId}")
    @Produces(MediaType.APPLICATION_XML)
    public String makePrivateLobby(@HeaderParam("securityToken") String securityToken,
                                   @PathParam("eventId") int eventId) {
        Long activePersonaId = tokenSessionBO.getActivePersonaId(securityToken);
        lobbyBO.createPrivateLobby(activePersonaId, eventId);
        return "";
    }

    @PUT
    @Secured
    @Path("/acceptinvite")
    @Produces(MediaType.APPLICATION_XML)
    public LobbyInfo acceptInvite(@HeaderParam("securityToken") String securityToken,
                                  @QueryParam("lobbyInviteId") Long lobbyInviteId) {

        Long activePersonaId = tokenSessionBO.getActivePersonaId(securityToken);
        tokenSessionBO.setActiveLobbyId(securityToken, lobbyInviteId);
        
        LobbyEntity lobbyInformation = lobbyDAO.findById(lobbyInviteId);
		System.out.println(lobbyInformation.getPersonaId() + " == " + activePersonaId);
		if(activePersonaId.equals(lobbyInformation.getPersonaId())) {
			System.out.println("Passed!");

			//eventname
			EventEntity eventInformation = lobbyInformation.getEvent();
			String eventNameFull = eventInformation.getName();
			String eventName = eventNameFull.split("\\(")[0];

			//personaname
			PersonaEntity personaEntity = personaDAO.findById(activePersonaId);
			
			//construct message
			String msg = "[" + personaEntity.getName() + "] is looking for racers on " + eventName;
			String msg_ds = "**" + personaEntity.getName() + "** is looking for racers on **" + eventName + "**";

			//send to discord
			if(parameterBO.getStrParam("DISCORD_WEBHOOK_LOBBY_URL") != null) {
				discord.sendMessage(msg_ds, 
					parameterBO.getStrParam("DISCORD_WEBHOOK_LOBBY_URL"), 
					parameterBO.getStrParam("DISCORD_WEBHOOK_LOBBY_NAME", "Botte"),
					0xbb00ff
				);
			}
        }

        return lobbyBO.acceptinvite(activePersonaId, lobbyInviteId);
    }

    @PUT
    @Secured
    @Path("/declineinvite")
    @Produces(MediaType.APPLICATION_XML)
    public String declineInvite(@HeaderParam("securityToken") String securityToken,
                                @QueryParam("lobbyInviteId") Long lobbyInviteId) {
        Long activePersonaId = tokenSessionBO.getActivePersonaId(securityToken);
        lobbyBO.declineinvite(activePersonaId, lobbyInviteId);
        return "";
    }

}
