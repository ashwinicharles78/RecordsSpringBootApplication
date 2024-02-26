package com.records.Records.Controller;


import com.records.Records.Entity.Mykey;
import com.records.Records.Entity.Records;
import com.records.Records.Repository.RecordsRepository;
import com.records.Records.service.RecordsService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class RecordsController {

    @Autowired
    private RecordsRepository repository;

    @Autowired
    private RecordsService service;

    @GetMapping(path="/records")
    public List<Records> getList() {
        return repository.findAll();
    }

    @PostMapping(path="/records")
    public Records saveRecord(@RequestBody Records records) {
        return service.saveRecord(records);
    }

    @PutMapping(path="/records/{Mykey}")
    public Records updateRecord(@RequestBody Records records, @PathVariable("Mykey") String mykey) {
        return service.updateRecord(new Mykey(mykey), records);
    }

    @DeleteMapping(path="/records/{Mykey}")
    public String deleteRecords(@PathVariable("Mykey") String mykey) {
        return service.deleteRecord(new Mykey(mykey));
    }

}
