package com.yingge.cloud.reminder.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReminderRepository extends JpaRepository<ReminderEntity, UUID> {
    List<ReminderEntity> findByCustomerIdInOrderByRemindAtAsc(Collection<UUID> customerIds);
    List<ReminderEntity> findByCustomerIdOrderByRemindAtDesc(UUID customerId);

    @Modifying
    @Query("""
            update ReminderEntity r set r.status='COMPLETED', r.completedAt=:now, r.updatedAt=:now
            where r.customerId=:customerId and r.status='PENDING'
            """)
    int completeActive(UUID customerId, Instant now);

    @Modifying
    @Query("""
            update ReminderEntity r
            set r.assignedMembershipId=:targetMembershipId, r.updatedAt=:now
            where r.customerId=:customerId and r.assignedMembershipId=:previousMembershipId
              and r.status='PENDING'
            """)
    int transferPending(UUID customerId, UUID previousMembershipId, UUID targetMembershipId, Instant now);

    @Modifying
    @Query("""
            update ReminderEntity r set r.status='CANCELLED', r.updatedAt=:now
            where r.customerId=:customerId and r.status='PENDING'
            """)
    int cancelActive(UUID customerId, Instant now);
}
