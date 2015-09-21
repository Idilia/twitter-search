package com.idilia.samples.ts.config;

import java.sql.SQLException;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.hibernate4.HibernateExceptionTranslator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Configure JPA beans. The data source is detected and configured
 * automatically by Spring Boot based on the packages included
 * in pom.xml.
 */
@Configuration
@EnableJpaRepositories(basePackages={"com.idilia.samples.ts.db"})
@EnableTransactionManagement
public class JPAConfiguration {

  @Bean
  public EntityManager entityManager(EntityManagerFactory emf) {
    return emf.createEntityManager();
  }

  @Bean
  public HibernateExceptionTranslator hibernateExceptionTranslator() {
    return new HibernateExceptionTranslator();
  }
  
  @Bean
  public JpaTransactionManager transactionManager(EntityManagerFactory emf) throws SQLException, ClassNotFoundException {
    JpaTransactionManager txManager = new JpaTransactionManager();
    txManager.setEntityManagerFactory(emf);
    return txManager;
  }
  
}
