package com.soapboxrace.core.dao;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

import com.soapboxrace.core.dao.util.BaseDAO;
import com.soapboxrace.core.jpa.CustomCarEntity;
import com.soapboxrace.core.jpa.SkillModPartEntity;

@Stateless
public class SkillModPartDAO extends BaseDAO<SkillModPartEntity> {

	@PersistenceContext
	protected void setEntityManager(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	public void deleteByCustomCar(CustomCarEntity customCarEntity) {
		Query query = entityManager.createNamedQuery("SkillModPartEntity.deleteByCustomCar");
		query.setParameter("customCar", customCarEntity);
		query.executeUpdate();
	}

}
