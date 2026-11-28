package za.co.salon.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import za.co.salon.shared.security.BusinessScoped;

import java.time.Instant;

/**
 * Blueprint 3.7: a supplier the salon buys stock from.
 */
@Getter
@Setter
@Entity
@Table(name = "suppliers")
public class Supplier implements BusinessScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long businessId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 150)
    private String contactPerson;

    @Column(length = 255)
    private String email;

    @Column(length = 30)
    private String phoneNumber;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
