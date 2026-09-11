package vn.aims.user.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "roles")
public class Role {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id") private Integer roleID;
    @Column(nullable = false, length = 50, unique = true) private String name;

    protected Role() { }
    public Integer getRoleID() { return roleID; }
    public String getName() { return name; }
}
