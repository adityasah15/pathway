package com.tcet.RestApi.repositorys;
import com.tcet.RestApi.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;
public interface StudentRepository extends JpaRepository<Student, Long> {
    boolean existsByRollNo(String rollNo);
    boolean existsByRollNoAndIdNot(String rollNo, Long id);
}
