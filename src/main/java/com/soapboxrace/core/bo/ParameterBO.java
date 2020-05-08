/*
 * This file is part of the Soapbox Race World core source code.
 * If you use any of this code for third-party purposes, please provide attribution.
 * Copyright (c) 2020.
 */

package com.soapboxrace.core.bo;

import com.soapboxrace.core.dao.ParameterDAO;
import com.soapboxrace.core.dao.TokenSessionDAO;
import com.soapboxrace.core.jpa.ParameterEntity;
import com.soapboxrace.core.jpa.TokenSessionEntity;
import com.soapboxrace.core.jpa.UserEntity;

import javax.annotation.PostConstruct;
import javax.ejb.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Startup
@Singleton
@Lock(LockType.READ)
public class ParameterBO {

    @EJB
    private ParameterDAO parameterDao;

    @EJB
    private TokenSessionDAO tokenDAO;

    private final ConcurrentMap<String, String> parameterMap;

    public ParameterBO() {
        parameterMap = new ConcurrentHashMap<>();
    }

    @PostConstruct
    public void init() {
        loadParameters();
    }

    /**
     * Loads parameters from the database
     */
    public void loadParameters() {
        System.out.println("Deprecated");
    }

    private String getParameter(String name) {
        try {
			ParameterEntity findById = parameterDao.findById(name);
			return findById.getValue();
		} catch (Exception e) {
            System.out.println("Cannot find " + name + " parameters on database");
		}
		return null;
    }

    public int getCarLimit(String securityToken) {
        TokenSessionEntity tokenSession = tokenDAO.findById(securityToken);
        return getCarLimit(tokenSession.getUserEntity());
    }

    public int getMaxLevel(String securityToken) {
        TokenSessionEntity tokenSession = tokenDAO.findById(securityToken);

        return getMaxLevel(tokenSession.getUserEntity());
    }

    public int getCarLimit(UserEntity userEntity) {
        if (userEntity.isPremium()) {
            return getIntParam("MAX_CAR_SLOTS_PREMIUM", 200);
        }
        return getIntParam("MAX_CAR_SLOTS_FREE", 200);
    }

    public int getMaxCash(UserEntity userEntity) {
        if (userEntity.isPremium()) {
            return getIntParam("MAX_PLAYER_CASH_PREMIUM", 9_999_999);
        }
        return getIntParam("MAX_PLAYER_CASH_FREE", 9_999_999);
    }

    public int getMaxLevel(UserEntity userEntity) {
        if (userEntity.isPremium()) {
            return getIntParam("MAX_PLAYER_LEVEL_PREMIUM", 60);
        }
        return getIntParam("MAX_PLAYER_LEVEL_FREE", 60);
    }

    public Integer getIntParam(String parameter) {
        String parameterFromDB = getParameter(parameter);

        if (parameterFromDB == null) {
            throw new RuntimeException("Cannot find integer parameter: " + parameter);
        }

        return Integer.valueOf(parameterFromDB);
    }

    public Integer getIntParam(String parameter, Integer defaultValue) {
        String parameterFromDB = getParameter(parameter);
        return parameterFromDB == null || parameterFromDB.isEmpty() ? defaultValue : Integer.valueOf(parameterFromDB);
    }

    public Boolean getBoolParam(String parameter) {
        String parameterFromDB = getParameter(parameter);
        return Boolean.valueOf(parameterFromDB);
    }

    public String getStrParam(String parameter) {
        return getParameter(parameter);
    }

    public String getStrParam(String parameter, String defaultValue) {
        String parameterFromDB = getParameter(parameter);

        return parameterFromDB == null || parameterFromDB.isEmpty() ? defaultValue : parameterFromDB;
    }

    public Float getFloatParam(String parameter) {
        String parameterFromDB = getParameter(parameter);

        if (parameterFromDB == null) {
            throw new RuntimeException("Cannot find float parameter: " + parameter);
        }

        return Float.valueOf(parameterFromDB);
    }

    public Float getFloatParam(String parameter, Float defaultValue) {
        String parameterFromDB = getParameter(parameter);
        return parameterFromDB == null || parameterFromDB.isEmpty() ? defaultValue : Float.valueOf(parameterFromDB);
    }
}