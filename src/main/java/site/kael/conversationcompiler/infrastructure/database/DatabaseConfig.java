package site.kael.conversationcompiler.infrastructure.database;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class DatabaseConfig {
    @Bean
    public DataSource dataSource(@Value("${spring.datasource.url}") String url,
                                 @Value("${spring.datasource.driver-class-name:org.sqlite.JDBC}") String driver) {
        createSqliteParentDirectory(url);
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setDriverClassName(driver);
        return dataSource;
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
