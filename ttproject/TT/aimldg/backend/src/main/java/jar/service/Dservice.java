package jar.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

import jar.model.Student;
import jar.repo.StudentRepo;

@Service
public class Dservice {

    private final StudentRepo db;

    
    public Dservice(StudentRepo db) {
        this.db = db;
    }

   
    public Map<Object, Object> createStudent(Student d) {
        Map<Object, Object> res = new HashMap<>();

        Student s = new Student();
        s.setName(d.getName());
        s.setEmail(d.getEmail());
        s.setIp(d.getIp());

        db.save(s);

        res.put("msg", "Student Added Successfully");
        res.put("status", 201);
        res.put("data", s);

        return res;
    }


    public Map<Object, Object> getAllStudents() {
        Map<Object, Object> res = new HashMap<>();
        
        res.put("msg", "Student Fetched Successfully");
        res.put("status", 200);
        res.put("data", db.findAll());

        return res;
    }

   
    public Map<Object, Object> updateStudent(Long id, Student d) {
        Map<Object, Object> res = new HashMap<>();
        Optional<Student> data = db.findById(id);

        if (data.isPresent()) {
            Student s = data.get();
            s.setName(d.getName());
            s.setEmail(d.getEmail());
            s.setIp(d.getIp());

            db.save(s);

            res.put("msg", "Student Updated Successfully");
            res.put("status", 200);
            res.put("data", s);
        } else {
            res.put("msg", "Student Not Found");
            res.put("status", 404);
        }

        return res;
    }

    
    public Map<Object, Object> deleteStudent(Long id) {
        Map<Object, Object> res = new HashMap<>();

        if (db.existsById(id)) {
            db.deleteById(id);
            res.put("msg", "Student Deleted Successfully");
            res.put("status", 204);
        } else {
            res.put("msg", "Student Not Found");
            res.put("status", 404);
        }
        
        return res;
    }
}
