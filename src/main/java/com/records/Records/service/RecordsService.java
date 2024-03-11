package com.records.Records.service;

import com.records.Records.Entity.Mykey;
import com.records.Records.Entity.Records;

public interface RecordsService {
    String saveRecord(Records records);

    Records updateRecord(Mykey mykey, Records records);

    String deleteRecord(Mykey mykey);
}
