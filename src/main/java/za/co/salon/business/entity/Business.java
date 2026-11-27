package za.co.salon.business.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Salon business information (blueprint 3.1). One business is one salon
 * location in the initial version.
 */
@Entity
@Table(name = "businesses")
@Getter
@Setter
public class Business {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String registrationNumber;

    @Column
    private String email;

    @Column(length = 30)
    private String phoneNumber;

    @Column(nullable = false)
    private String addressLine1;

    @Column
    private String addressLine2;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 100)
    private String province;

    @Column(length = 20)
    private String postalCode;

    @Column(nullable = false, length = 2, columnDefinition = "CHAR(2)")
    private String countryCode = "ZA";

    @Column(nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currencyCode = "ZAR";

    @Column(nullable = false, length = 60)
    private String timezone = "Africa/Johannesburg";

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(nullable = false)
    private boolean vatRegistered = false;

    @Column(length = 50)
    private String vatNumber;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

}
