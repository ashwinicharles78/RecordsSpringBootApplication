package com.records.Records.Controller;


import com.records.Records.Entity.Mykey;
import com.records.Records.Entity.Records;
import com.records.Records.Repository.RecordsRepository;
import com.records.Records.service.RecordsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class RecordsController {

    @Autowired
    private RecordsRepository repository;

    @Autowired
    private RecordsService service;

    @PreAuthorize("hasRole('READER')")
    @GetMapping(path="/records")
    public List<Records> getList() {
        return repository.findAll();
    }

    @PreAuthorize("hasRole('WRITER')")
    @PostMapping(path="/records")
    public Records saveRecord(@RequestBody Records records) {
        return service.saveRecord(records);
    }

    @PreAuthorize("hasRole('WRITER')")
    @PutMapping(path="/records/{Mykey}")
    public Records updateRecord(@RequestBody Records records, @PathVariable("Mykey") String mykey) {
        return service.updateRecord(new Mykey(mykey), records);
    }

    @PreAuthorize("hasRole('WRITER')")
    @DeleteMapping(path="/records/{Mykey}")
    public String deleteRecords(@PathVariable("Mykey") String mykey) {
        return service.deleteRecord(new Mykey(mykey));
    }

}
