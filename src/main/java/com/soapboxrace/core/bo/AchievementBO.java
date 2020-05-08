/*
 * This file is part of the Soapbox Race World core source code.
 * If you use any of this code for third-party purposes, please provide attribution.
 * Copyright (c) 2020.
 */

package com.soapboxrace.core.bo;

import com.soapboxrace.core.bo.util.AchievementProgressionContext;
import com.soapboxrace.core.bo.util.AchievementUpdateInfo;
import com.soapboxrace.core.dao.*;
import com.soapboxrace.core.engine.EngineException;
import com.soapboxrace.core.engine.EngineExceptionCode;
import com.soapboxrace.core.jpa.*;
import com.soapboxrace.core.xmpp.OpenFireSoapBoxCli;
import com.soapboxrace.jaxb.http.*;
import com.soapboxrace.jaxb.xmpp.AchievementAwarded;
import com.soapboxrace.jaxb.xmpp.AchievementProgress;
import com.soapboxrace.jaxb.xmpp.AchievementsAwarded;
import com.soapboxrace.jaxb.xmpp.XMPP_ResponseTypeAchievementsAwarded;

import javax.ejb.*;
import javax.script.ScriptException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

@Singleton
@Lock(LockType.READ)
public class AchievementBO {
    @EJB
    private ItemRewardBO itemRewardBO;
    @EJB
    private DriverPersonaBO driverPersonaBO;
    @EJB
    private ScriptingBO scriptingBO;
    @EJB
    private PersonaAchievementDAO personaAchievementDAO;
    @EJB
    private PersonaAchievementRankDAO personaAchievementRankDAO;
    @EJB
    private AchievementDAO achievementDAO;
    @EJB
    private AchievementRankDAO achievementRankDAO;
    @EJB
    private AchievementRewardDAO achievementRewardDAO;
    @EJB
    private BadgeDefinitionDAO badgeDefinitionDAO;
    @EJB
    private PersonaDAO personaDAO;
    @EJB
    private OpenFireSoapBoxCli openFireSoapBoxCli;

    @Schedule(minute = "*/30", hour = "*")
    public void updateRankRarities() {
        Long countPersonas = personaDAO.countPersonas();

        if (countPersonas == 0L) return;

        for (AchievementEntity achievementEntity : achievementDAO.findAll()) {
            for (AchievementRankEntity achievementRankEntity : achievementEntity.getRanks()) {
                achievementRankEntity.setRarity(((float) personaAchievementRankDAO.countPersonasWithRank(achievementRankEntity.getId())) / countPersonas);
                achievementRankDAO.update(achievementRankEntity);
            }
        }
    }

    /**
     * Creates a new achievement transaction for the given persona ID
     *
     * @param personaId the persona ID
     * @return new {@link AchievementTransaction} instance
     */
    public AchievementTransaction createTransaction(Long personaId) {
        return new AchievementTransaction(personaId);
    }

    /**
     * Commits the changes for the given {@link AchievementTransaction} instance.
     *
     * @param personaEntity the {@link PersonaEntity} instance
     * @param transaction   the {@link AchievementTransaction} instance
     */
    @Asynchronous
    public void commitTransaction(PersonaEntity personaEntity, AchievementTransaction transaction) {
        transaction.getEntries().forEach((k, v) -> v.forEach(m -> updateAchievements(personaEntity, k, m)));
        personaDAO.update(personaEntity);

        transaction.markCommitted();
        transaction.clear();
    }

    public AchievementRewards redeemReward(Long personaId, Long achievementRankId) {
        PersonaAchievementRankEntity personaAchievementRankEntity =
                personaAchievementRankDAO.findByPersonaIdAndAchievementRankId(personaId, achievementRankId);

        if (personaAchievementRankEntity == null) {
            throw new IllegalArgumentException(personaId + " does not have " + achievementRankId);
        }

        if (!"RewardWaiting".equals(personaAchievementRankEntity.getState())) {
            throw new IllegalArgumentException(personaId + " has no reward for " + achievementRankId);
        }

        AchievementRewards achievementRewards = new AchievementRewards();
        achievementRewards.setAchievementRankId(achievementRankId);
        achievementRewards.setVisualStyle(personaAchievementRankEntity.getAchievementRankEntity().getRewardVisualStyle());
        achievementRewards.setStatus(CommerceResultStatus.SUCCESS);
        achievementRewards.setCommerceItems(
                itemRewardBO.getRewards(
                        personaId,
                        achievementRewardDAO.findByDescription(personaAchievementRankEntity.getAchievementRankEntity().getRewardDescription())
                                .getRewardScript()));
        achievementRewards.setInventoryItems(new ArrayOfInventoryItemTrans());
        achievementRewards.setPurchasedCars(new ArrayOfOwnedCarTrans());
        achievementRewards.setWallets(new ArrayOfWalletTrans());

        achievementRewards.getWallets().getWalletTrans().add(new WalletTrans() {{
            setBalance(personaDAO.findById(personaId).getCash());
            setCurrency("CASH");
        }});

        personaAchievementRankEntity.setState("Completed");
        personaAchievementRankDAO.update(personaAchievementRankEntity);

        return achievementRewards;
    }

    public AchievementsPacket loadAll(Long personaId) {
        if (personaId.equals(0L)) {
            throw new EngineException(EngineExceptionCode.FailedSessionSecurityPolicy, true);
        }

        AchievementsPacket achievementsPacket = new AchievementsPacket();
        achievementsPacket.setPersonaId(personaId);
        achievementsPacket.setBadges(new ArrayOfBadgeDefinitionPacket());
        achievementsPacket.setDefinitions(new ArrayOfAchievementDefinitionPacket());

        for (AchievementEntity achievementEntity : achievementDAO.findAll()) {
            AchievementDefinitionPacket achievementDefinitionPacket = new AchievementDefinitionPacket();

            achievementDefinitionPacket.setAchievementDefinitionId(achievementEntity.getId().intValue());
            achievementDefinitionPacket.setAchievementRanks(new ArrayOfAchievementRankPacket());
            achievementDefinitionPacket.setBadgeDefinitionId(achievementEntity.getBadgeDefinitionEntity().getId().intValue());
            achievementDefinitionPacket.setIsVisible(achievementEntity.getVisible());
            achievementDefinitionPacket.setProgressText(achievementEntity.getProgressText());
            achievementDefinitionPacket.setStatConversion(StatConversion.fromValue(achievementEntity.getStatConversion()));

            PersonaAchievementEntity personaAchievementEntity =
                    personaAchievementDAO.findByPersonaIdAndAchievementId(personaId, achievementEntity.getId());

            if (personaAchievementEntity != null) {
                achievementDefinitionPacket.setCanProgress(personaAchievementEntity.isCanProgress());
                achievementDefinitionPacket.setCurrentValue(personaAchievementEntity.getCurrentValue());
            } else {
                achievementDefinitionPacket.setCanProgress(true);
            }

            for (AchievementRankEntity achievementRankEntity : achievementEntity.getRanks()) {
                AchievementRankPacket rankPacket = new AchievementRankPacket();
                rankPacket.setAchievedOn("0001-01-01T00:00:00");
                rankPacket.setIsRare(achievementRankEntity.getRarity() <= 0.05f); // <= threshold for "rare" is <= 5%
                rankPacket.setRarity(achievementRankEntity.getRarity());
                rankPacket.setRank(achievementRankEntity.getRank().shortValue());
                rankPacket.setRewardDescription(achievementRankEntity.getRewardDescription());
                rankPacket.setRewardType(achievementRankEntity.getRewardType());
                rankPacket.setRewardVisualStyle(achievementRankEntity.getRewardVisualStyle());
                rankPacket.setPoints(achievementRankEntity.getPoints().shortValue());
                rankPacket.setAchievementRankId(achievementRankEntity.getId().intValue());
                rankPacket.setState(AchievementState.LOCKED);
                rankPacket.setThresholdValue(achievementRankEntity.getThresholdValue());

                PersonaAchievementRankEntity personaAchievementRankEntity =
                        personaAchievementRankDAO.findByPersonaIdAndAchievementRankId(personaId,
                                achievementRankEntity.getId());

                if (personaAchievementRankEntity != null) {
                    rankPacket.setState(AchievementState.fromValue(personaAchievementRankEntity.getState()));

                    if (personaAchievementRankEntity.getAchievedOn() != null) {
                        rankPacket.setAchievedOn(personaAchievementRankEntity.getAchievedOn().format(DateTimeFormatter.ofPattern("yyyyy-MM-dd'T'hh:mm:ss")));
                    }
                }

                achievementDefinitionPacket.getAchievementRanks().getAchievementRankPacket().add(rankPacket);
            }

            achievementsPacket.getDefinitions().getAchievementDefinitionPacket().add(achievementDefinitionPacket);
        }

        for (BadgeDefinitionEntity badgeDefinitionEntity : badgeDefinitionDAO.findAll()) {
            BadgeDefinitionPacket badgeDefinitionPacket = new BadgeDefinitionPacket();
            badgeDefinitionPacket.setBackground(badgeDefinitionEntity.getBackground());
            badgeDefinitionPacket.setBorder(badgeDefinitionEntity.getBorder());
            badgeDefinitionPacket.setDescription(badgeDefinitionEntity.getDescription());
            badgeDefinitionPacket.setIcon(badgeDefinitionEntity.getIcon());
            badgeDefinitionPacket.setName(badgeDefinitionEntity.getName());
            badgeDefinitionPacket.setBadgeDefinitionId(badgeDefinitionEntity.getId().intValue());
            achievementsPacket.getBadges().getBadgeDefinitionPacket().add(badgeDefinitionPacket);
        }

        return achievementsPacket;
    }

    /**
     * Update all appropriate achievements in the given category for the persona by the given ID
     *
     * @param personaEntity       The {@link PersonaEntity} instance to be updated
     * @param achievementCategory The category of achievements to evaluate
     * @param properties          Relevant contextual information for achievements.
     */
    private void updateAchievements(PersonaEntity personaEntity, String achievementCategory,
                                    Map<String, Object> properties) {
        int originalScore = personaEntity.getScore();
        int newScore = originalScore;
        properties = new HashMap<>(properties);

        for (AchievementEntity achievementEntity : achievementDAO.findAllByCategory(achievementCategory)) {
            if (!achievementEntity.getAutoUpdate()) continue;

            if (achievementEntity.getUpdateTrigger() == null || achievementEntity.getUpdateTrigger().trim().isEmpty()) {
                continue;
            }

            // Locate persona-specific achievement data. Create it if it does not exist
            PersonaAchievementEntity personaAchievementEntity = personaAchievementDAO.findByPersonaIdAndAchievementId(
                    personaEntity.getPersonaId(), achievementEntity.getId());
            boolean insert = false;

            if (personaAchievementEntity == null) {
                personaAchievementEntity = new PersonaAchievementEntity();
                personaAchievementEntity.setCanProgress(true);
                personaAchievementEntity.setCurrentValue(0L);
                personaAchievementEntity.setAchievementEntity(achievementEntity);
                personaAchievementEntity.setPersonaEntity(personaEntity);
                insert = true;
            }

            properties.put("personaAchievement", personaAchievementEntity);

            try {
                Boolean shouldUpdate = (Boolean) scriptingBO.eval(achievementEntity.getUpdateTrigger(),
                        properties);
                if (shouldUpdate) {
                    if (insert)
                        personaAchievementDAO.insert(personaAchievementEntity);
                    AchievementUpdateInfo achievementUpdateInfo = updateAchievement(personaEntity, achievementEntity, properties, personaAchievementEntity);
                    sendUpdateInfoMessage(personaEntity.getPersonaId(), achievementUpdateInfo);
                    newScore += achievementUpdateInfo.getPointsGiven();
                }
            } catch (ScriptException ex) {
                ex.printStackTrace();
            }
        }

        if (newScore != originalScore) {
            AchievementProgressionContext progressionContext = new AchievementProgressionContext(0, 0,
                    personaEntity.getLevel(), personaEntity.getScore(), 0, false, true,
                    false, false);

            updateAchievements(personaEntity, "PROGRESSION",
                    Map.of("persona", personaEntity, "progression", progressionContext));
        }

        personaEntity.setScore(newScore);
    }

    private void sendUpdateInfoMessage(Long personaId, AchievementUpdateInfo achievementUpdateInfo) {
        XMPP_ResponseTypeAchievementsAwarded responseTypeAchievementsAwarded =
                new XMPP_ResponseTypeAchievementsAwarded();

        for (AchievementUpdateInfo.CompletedAchievementRank completedAchievementRank : achievementUpdateInfo.getCompletedAchievementRanks()) {
            AchievementsAwarded achievementsAwarded = new AchievementsAwarded();
            achievementsAwarded.setAchievements(new ArrayList<>());
            achievementsAwarded.setBadges(driverPersonaBO.getBadges(personaId).getBadgePacket());
            achievementsAwarded.setProgressed(new ArrayList<>());
            achievementsAwarded.setPersonaId(personaId);
            AchievementAwarded achievementAwarded = new AchievementAwarded();
            LocalDateTime achievedOn = completedAchievementRank.getAchievedOn();
            AchievementRankEntity achievementRankEntity = completedAchievementRank.getAchievementRankEntity();
            AchievementEntity achievementEntity = completedAchievementRank.getAchievementEntity();
            achievementAwarded.setAchievedOn(achievedOn.format(DateTimeFormatter.ofPattern("yyyyy-MM-dd'T'hh:mm:ss")));
            achievementAwarded.setAchievementDefinitionId(achievementEntity.getId());
            achievementAwarded.setAchievementRankId(achievementRankEntity.getId());
            achievementAwarded.setDescription(achievementEntity.getBadgeDefinitionEntity().getDescription());
            achievementAwarded.setIcon(achievementEntity.getBadgeDefinitionEntity().getIcon());
            achievementAwarded.setName(achievementEntity.getBadgeDefinitionEntity().getName());
            achievementAwarded.setRarity(achievementRankEntity.getRarity());
            achievementAwarded.setPoints(achievementRankEntity.getPoints());
            achievementsAwarded.getAchievements().add(achievementAwarded);
            responseTypeAchievementsAwarded.setAchievementsAwarded(achievementsAwarded);

            openFireSoapBoxCli.send(responseTypeAchievementsAwarded, personaId);
        }

        for (AchievementUpdateInfo.ProgressedAchievement progressedAchievement : achievementUpdateInfo.getProgressedAchievements()) {
            AchievementsAwarded achievementsAwarded = new AchievementsAwarded();
            achievementsAwarded.setAchievements(new ArrayList<>());
            achievementsAwarded.setBadges(driverPersonaBO.getBadges(personaId).getBadgePacket());
            achievementsAwarded.setProgressed(new ArrayList<>());
            achievementsAwarded.setPersonaId(personaId);
            AchievementProgress progress = new AchievementProgress();
            progress.setAchievementDefinitionId(progressedAchievement.getAchievementDefinitionId());
            progress.setCurrentValue(progressedAchievement.getValue());
            achievementsAwarded.getProgressed().add(progress);
            responseTypeAchievementsAwarded.setAchievementsAwarded(achievementsAwarded);

            openFireSoapBoxCli.send(responseTypeAchievementsAwarded, personaId);
        }
    }

    private AchievementUpdateInfo updateAchievement(PersonaEntity personaEntity, AchievementEntity achievementEntity, Map<String, Object> bindings,
                                                    PersonaAchievementEntity personaAchievementEntity) {
        // If no progression can be made, there's nothing to do.
        if (!personaAchievementEntity.isCanProgress()) {
            return new AchievementUpdateInfo();
        }

        Integer pointsAdded = 0;

        // Determine the value to add to the achievement progress.
        try {
            Long cleanVal = 0L;

            if (achievementEntity.getUpdateValue() == null || achievementEntity.getUpdateValue().trim().isEmpty()) {
                return new AchievementUpdateInfo();
            }

            AchievementUpdateInfo achievementUpdateInfo = new AchievementUpdateInfo();
            Object rawVal = scriptingBO.eval(achievementEntity.getUpdateValue(), bindings);

            if (rawVal instanceof Integer) {
                cleanVal = ((Integer) rawVal).longValue();
            } else if (rawVal instanceof Long) {
                cleanVal = (Long) rawVal;
            } else if (rawVal instanceof Float) {
                if (Float.isNaN((Float) rawVal)) {
                    throw new RuntimeException("Float return value is NaN! Script: " + achievementEntity.getUpdateValue());
                }

                cleanVal = (long) Math.round((Float) rawVal);
            } else if (rawVal instanceof Double) {
                if (Double.isNaN((Double) rawVal)) {
                    throw new RuntimeException("Double return value is NaN! Script: " + achievementEntity.getUpdateValue());
                }

                cleanVal = Math.round((Double) rawVal);
            }


            OptionalInt maxVal = achievementEntity.getRanks().stream()
                    .mapToInt(AchievementRankEntity::getThresholdValue)
                    .max();
            if (maxVal.isPresent()) {
                long newVal = Math.max(0, Math.min(maxVal.getAsInt(),
                        achievementEntity.getShouldOverwriteProgress() ? cleanVal :
                                (personaAchievementEntity.getCurrentValue() + cleanVal)));
                if (newVal == 0L) {
                    achievementUpdateInfo.getProgressedAchievements().add(new AchievementUpdateInfo.ProgressedAchievement(achievementEntity.getId(), newVal));
                } else {
                    for (int i = 0; i < achievementEntity.getRanks().size(); i++) {
                        AchievementRankEntity previous = null;
                        AchievementRankEntity current = achievementEntity.getRanks().get(i);
                        PersonaAchievementRankEntity previousRank = null;
                        PersonaAchievementRankEntity currentRank =
                                personaAchievementRankDAO.findByPersonaIdAndAchievementRankId(personaEntity.getPersonaId(),
                                        current.getId());

                        if (i > 0) {
                            previous = achievementEntity.getRanks().get(i - 1);
                            previousRank =
                                    personaAchievementRankDAO.findByPersonaIdAndAchievementRankId(personaEntity.getPersonaId(),
                                            previous.getId());

                            if (previousRank == null) {
                                previousRank = createPersonaAchievementRank(personaAchievementEntity, previous);
                            }
                        }

                        if (currentRank == null) {
                            currentRank = createPersonaAchievementRank(personaAchievementEntity, current);
                        }

                        long threshold = current.getThresholdValue();

                        if (currentRank.getState().equals("Completed") || currentRank.getState().equals("RewardWaiting"))
                            continue;

                        if (newVal >= threshold && newVal != personaAchievementEntity.getCurrentValue()) {
                            currentRank.setState("RewardWaiting");
                            currentRank.setAchievedOn(LocalDateTime.now());
                            personaAchievementRankDAO.update(currentRank);
                            pointsAdded += current.getPoints();

                            achievementUpdateInfo.getCompletedAchievementRanks().add(new AchievementUpdateInfo.CompletedAchievementRank(
                                    achievementEntity, current, currentRank.getAchievedOn()));
                        } else if (previous != null && previousRank.getState().equals("InProgress")) {
                            currentRank.setState("Locked");
                            personaAchievementRankDAO.update(currentRank);
                            break;
                        } else if (newVal > 0 && newVal != personaAchievementEntity.getCurrentValue() && newVal < threshold) {
                            currentRank.setState("InProgress");
                            personaAchievementRankDAO.update(currentRank);
                            achievementUpdateInfo.getProgressedAchievements().add(new AchievementUpdateInfo.ProgressedAchievement(achievementEntity.getId(), newVal));
                            break;
                        }
                    }
                }

                personaAchievementEntity.setCurrentValue(newVal);
                personaAchievementEntity.setCanProgress(newVal < maxVal.getAsInt());
                personaAchievementDAO.update(personaAchievementEntity);
            }

            achievementUpdateInfo.setPointsGiven(pointsAdded);
            return achievementUpdateInfo;
        } catch (ScriptException ex) {
            throw new EngineException(ex, EngineExceptionCode.UnspecifiedError, true);
        }
    }

    private PersonaAchievementRankEntity createPersonaAchievementRank(PersonaAchievementEntity personaAchievementEntity, AchievementRankEntity achievementRankEntity) {
        PersonaAchievementRankEntity rankEntity = new PersonaAchievementRankEntity();
        rankEntity.setState("Locked");
        rankEntity.setPersonaAchievementEntity(personaAchievementEntity);
        rankEntity.setAchievementRankEntity(achievementRankEntity);
        personaAchievementRankDAO.insert(rankEntity);
        return rankEntity;
    }
}
