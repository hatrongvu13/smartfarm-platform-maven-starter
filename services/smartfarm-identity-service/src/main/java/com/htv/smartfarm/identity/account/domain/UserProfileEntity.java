package com.htv.smartfarm.identity.account.domain;

import com.htv.smartfarm.identity.shared.persistence.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "sf_user_profile")
public class UserProfileEntity extends AuditableEntity {

    @Id
    @Column(name = "user_id", length = 36)
    private String userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(
                    name = "fk_user_profile_account"
            )
    )
    private UserAccountEntity account;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Column(name = "locale", nullable = false, length = 20)
    private String locale;

    @Column(name = "time_zone", nullable = false, length = 50)
    private String timeZone;

    protected UserProfileEntity() {
    }

    public UserProfileEntity(
            UserAccountEntity account,
            String displayName,
            String locale,
            String timeZone
    ) {
        this.account = account;
        this.displayName = displayName;
        this.locale = locale;
        this.timeZone = timeZone;
    }

    public void update(
            String displayName,
            String phoneNumber,
            String locale,
            String timeZone
    ) {
        this.displayName = displayName;
        this.phoneNumber = phoneNumber;
        this.locale = locale;
        this.timeZone = timeZone;
    }

    public String getUserId() {
        return userId;
    }

    public UserAccountEntity getAccount() {
        return account;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getLocale() {
        return locale;
    }

    public String getTimeZone() {
        return timeZone;
    }
}