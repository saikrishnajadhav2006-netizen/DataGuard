package jar.controller;

import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jar.service.Dservice;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class Delete {

    @Autowired
    private Dservice service;

    @DeleteMapping("/delete/{id}")
    public Map<Object, Object> delete(@PathVariable Long id) {
        return service.deleteStudent(id);
    }
}
