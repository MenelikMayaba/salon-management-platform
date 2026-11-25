package za.co.salon.business.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Connects users to businesses (blueprint 3.1). The user's role lives here,
 * not on the user account - one user can be OWNER of one salon and STYLIST
 * of another. Foreign keys are plain Long columns rather than entity
 * associations, keeping the module structure decoupled.
 */
@Entity
@Table(name = "business_members",
        uniqueConstraints = @UniqueConstraint(name = "uk_business_members_pair", columnNames = {"business_id", "user_id"}))
public class BusinessMember {

    public enum Role {
        OWNER, MANAGER, RECEPTIONIST, STYLIST, ACCOUNTANT
    }

    public enum Status {
        ACTIVE, SUSPENDED, INVITED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long businessId;

    @Column(nullable = false)
    private Long userId;

    // Hibernate 6 generates native MySQL enum columns for STRING enums; the schema
    // contract specifies varchar so roles can evolve without ALTER TABLE. Same
    // override pattern as the tinyint boolean mapping from phase 1.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private Role role;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private Instant joinedAt;

    @PrePersist
    void onCreate() {
        this.joinedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBusinessId() {
        return businessId;
    }

    public void setBusinessId(Long businessId) {
        this.businessId = businessId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
