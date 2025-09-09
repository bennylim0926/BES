package com.example.BES.respositories;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.BES.models.Judge;

@Repository
public interface JudgeRepo extends JpaRepository<Judge,Long>{
    Optional<Judge> findByName(String name);
} 
