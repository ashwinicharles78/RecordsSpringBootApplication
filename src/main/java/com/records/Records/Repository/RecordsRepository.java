package com.records.Records.Repository;

import com.records.Records.Entity.Mykey;
import com.records.Records.Entity.Records;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecordsRepository extends JpaRepository<Records, Mykey> {

    List<Records> findByMykey(Mykey mykey);

    @Transactional
    void deleteByMykey(Mykey mykey);
}
