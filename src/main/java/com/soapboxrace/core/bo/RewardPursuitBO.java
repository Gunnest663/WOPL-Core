/*
 * This file is part of the Soapbox Race World core source code.
 * If you use any of this code for third-party purposes, please provide attribution.
 * Copyright (c) 2020.
 */

package com.soapboxrace.core.bo;

import com.soapboxrace.core.bo.util.RewardVO;
import com.soapboxrace.core.dao.PersonaDAO;
import com.soapboxrace.core.jpa.*;
import com.soapboxrace.jaxb.http.Accolades;
import com.soapboxrace.jaxb.http.EnumRewardType;
import com.soapboxrace.jaxb.http.PursuitArbitrationPacket;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import java.util.Random;

@Stateless
public class RewardPursuitBO extends RewardBO {

    @EJB
    private PersonaDAO personaDao;

    @EJB
    private LegitRaceBO legitRaceBO;

    public Accolades getPursuitAccolades(Long activePersonaId, PursuitArbitrationPacket pursuitArbitrationPacket,
                                         EventDataEntity eventDataEntity, EventSessionEntity eventSessionEntity,
                                         Boolean isBusted, AchievementTransaction achievementTransaction) {
        int finishReason = pursuitArbitrationPacket.getFinishReason();
        boolean legit = legitRaceBO.isLegit(activePersonaId, pursuitArbitrationPacket, eventSessionEntity);
        eventDataEntity.setLegit(legit);
        if (!legit || isBusted || finishReason != 518) {
            return new Accolades();
        }
        EventEntity eventEntity = eventSessionEntity.getEvent();
        PersonaEntity personaEntity = personaDao.findById(activePersonaId);
        RewardVO rewardVO = getRewardVO(personaEntity);

        setPursuitRewards(personaEntity, eventEntity, pursuitArbitrationPacket, rewardVO);

        Random random = new Random();
        pursuitArbitrationPacket.setRank(random.nextInt(4 - 1) + 1);
        applyRaceReward(rewardVO.getRep(), rewardVO.getCash(), personaEntity, true, achievementTransaction);
        return getAccolades(personaEntity, eventEntity, pursuitArbitrationPacket, rewardVO);
    }

    public void setPursuitRewards(PersonaEntity personaEntity, EventEntity eventEntity,
                                  PursuitArbitrationPacket pursuitArbitrationPacket, RewardVO rewardVO) {
        setBaseReward(personaEntity, eventEntity, pursuitArbitrationPacket, rewardVO);
        setPursitParamReward(pursuitArbitrationPacket.getCopsDeployed(), EnumRewardType.COP_CARS_DEPLOYED, rewardVO);
        setPursitParamReward(pursuitArbitrationPacket.getCopsDisabled(), EnumRewardType.COP_CARS_DISABLED, rewardVO);
        setPursitParamReward(pursuitArbitrationPacket.getCopsRammed(), EnumRewardType.COP_CARS_RAMMED, rewardVO);
        setPursitParamReward(pursuitArbitrationPacket.getCostToState(), EnumRewardType.COST_TO_STATE, rewardVO);
        setPursitParamReward(pursuitArbitrationPacket.getEventDurationInMilliseconds(), EnumRewardType.PURSUIT_LENGTH
                , rewardVO);
        setPursitParamReward(pursuitArbitrationPacket.getHeat(), EnumRewardType.HEAT_LEVEL, rewardVO);
        setPursitParamReward(pursuitArbitrationPacket.getInfractions(), EnumRewardType.INFRACTIONS, rewardVO);
        setPursitParamReward(pursuitArbitrationPacket.getRoadBlocksDodged(), EnumRewardType.ROADBLOCKS_DODGED,
                rewardVO);
        setPursitParamReward(pursuitArbitrationPacket.getSpikeStripsDodged(), EnumRewardType.SPIKE_STRIPS_DODGED,
                rewardVO);

        setTopSpeedReward(eventEntity, pursuitArbitrationPacket.getTopSpeed(), rewardVO);
        setSkillMultiplierReward(personaEntity, rewardVO, SkillModRewardType.BOUNTY_HUNTER);
        setMultiplierReward(eventEntity, rewardVO);
        setAmplifierReward(personaEntity, rewardVO);
    }
}
