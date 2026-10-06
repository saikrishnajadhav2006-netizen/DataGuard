package jar.model;

import jakarta.persistence.Entity;
import jakarta.persistence.*;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "students")
public class Student {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    @Column(unique=true)
    private String email;
    private String ip;

    public String getName() {
        return this.name;
    }

    public Long getId() {
        return this.id;
    }

    public String getEmail() {
        return this.email;
    }

  public String getIp() {
    return this.ip;
}

    public void setName(String name) {
        this.name = name;
    }

    public void setEmail(String e) {
        this.email = e;
    }

    public void setIp(String i) {
        this.ip = i;
    }

}
