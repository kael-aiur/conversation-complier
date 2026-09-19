package site.kael.conversationcompiler.infrastructure.database;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class DatabaseConfig {
    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSource dataSource(DataSourceProperties properties) {
        createSqliteParentDirectory(properties.determineUrl());
        return properties.initializeDataSourceBuilder().build();
    }

    private void createSqliteParentDirectory(String url) {
        if (url == null || !url.startsWith("jdbc:sqlite:")) return;
        String location = url.substring("jdbc:sqlite:".length());
        if (location.equals(":memory:") || location.startsWith("file::memory:")) return;
        try {
            Path path = Path.of(location).toAbsolutePath();
            if (path.getParent() != null) Files.createDirectories(path.getParent());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to create SQLite database directory", ex);
        }
    }
}
