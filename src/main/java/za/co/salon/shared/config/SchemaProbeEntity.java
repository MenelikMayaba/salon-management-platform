package za.co.salon.shared.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * Proves the schema-contract type mappings before they are replicated across the
 * real entities (blueprint section 3). Verify the generated table with
 * SHOW CREATE TABLE schema_probe and compare column types against
 * docs/phases/phase-01-project-foundation.md. Not business functionality.
 */
@Entity
@Table(name = "schema_probe")
@Getter
@Setter
public class SchemaProbeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(precision = 7, scale = 4)
    private BigDecimal rate;

    @Column(precision = 12, scale = 4)
    private BigDecimal quantity;

    /**
     * Hibernate 6 maps boolean to bit(1) on MySQL by default. The schema
     * contract requires tinyint semantics (blueprint 2.6), so every boolean
     * in the project carries SqlTypes.TINYINT.
     */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "active_flag")
    private boolean activeFlag;

    @Column(nullable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> payload;

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }
}
