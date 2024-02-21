package com.records.Records.service.impl;

import com.records.Records.Entity.Mykey;
import com.records.Records.Entity.Records;
import com.records.Records.Repository.RecordsRepository;
import com.records.Records.service.RecordsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class RecordsServiceImpl implements RecordsService {

    @Autowired
    private RecordsRepository repository;

    @Override
    public Records saveRecord(Records records) {
        return repository.save(records);
    }

    @Override
    public Records updateRecord(Mykey mykey, Records records) {
        Records originalRecord = repository.findByMykey(mykey).isEmpty() ?
                null : repository.findByMykey(mykey).get(0);

        if(Objects.nonNull(originalRecord)) {
            if(Objects.nonNull(records.getActive())) {
                originalRecord.setActive(records.getActive());
            }
            return repository.save(originalRecord);
        }
        return null;
    }

    @Override
    public String deleteRecord(Mykey mykey) {
        if(!repository.findByMykey(mykey).isEmpty()) {
            repository.deleteByMykey(mykey);
            return "Deleted Successfully";
        }
        return "Unable to find Record";
    }
}
