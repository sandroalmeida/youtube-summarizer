package com.sandroalmeida.youtubesummarizer.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.Properties;

/**
 * Spring Data JPA configuration class.
 * Configures JPA, Hibernate, and database connection pooling.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.sandroalmeida.youtubesummarizer.repository")
@EnableTransactionManagement
public class JpaConfig {

    private final ConfigManager configManager;

    public JpaConfig() {
        this.configManager = ConfigManager.getInstance();
    }

    /**
     * Configure HikariCP connection pool data source
     */
    @Bean
    public DataSource dataSource() {
        HikariConfig hikariConfig = new HikariConfig();
        
        hikariConfig.setJdbcUrl(configManager.getString("db.url"));
        hikariConfig.setUsername(configManager.getString("db.username"));
        hikariConfig.setPassword(configManager.getString("db.password", ""));
        hikariConfig.setDriverClassName(configManager.getString("db.driver", "com.mysql.cj.jdbc.Driver"));
        
        // Connection pool settings
        hikariConfig.setMaximumPoolSize(configManager.getInt("db.hikari.maximum-pool-size", 10));
        hikariConfig.setMinimumIdle(configManager.getInt("db.hikari.minimum-idle", 5));
        hikariConfig.setConnectionTimeout(configManager.getLong("db.hikari.connection-timeout", 30000));
        hikariConfig.setIdleTimeout(configManager.getLong("db.hikari.idle-timeout", 600000));
        hikariConfig.setMaxLifetime(configManager.getLong("db.hikari.max-lifetime", 1800000));
        
        // Connection pool name for monitoring
        hikariConfig.setPoolName("LinkedInScraperHikariPool");
        
        return new HikariDataSource(hikariConfig);
    }

    /**
     * Configure EntityManagerFactory with Hibernate
     */
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.sandroalmeida.youtubesummarizer.model");
        
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);
        
        Properties properties = new Properties();
        properties.setProperty("hibernate.hbm2ddl.auto", "none"); // Don't auto-create tables
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.MySQL8Dialect");
        properties.setProperty("hibernate.show_sql", "false");
        properties.setProperty("hibernate.format_sql", "true");
        properties.setProperty("hibernate.use_sql_comments", "true");
        
        em.setJpaProperties(properties);
        
        return em;
    }

    /**
     * Configure transaction manager
     */
    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory);
        return transactionManager;
    }
}

