package com.marketplace.laptop.repository;

import com.marketplace.laptop.entity.Laptop;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LaptopRepository extends JpaRepository<Laptop, UUID>, JpaSpecificationExecutor<Laptop> {

    @Query("""
            select l from Laptop l
              join fetch l.subBrand sb
              join fetch sb.brand
              join fetch l.cpu
              left join fetch l.gpu
            where l.id in :ids
            """)
    List<Laptop> findAllByIdWithJoins(List<UUID> ids);

    /**
     * Row-locked read used when placing an order. Two customers closing on the
     * last unit at the same instant must not both succeed -- the agent has no
     * way to prevent that, so the database does.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Laptop l where l.id = :id")
    Optional<Laptop> findByIdForUpdate(UUID id);
}
