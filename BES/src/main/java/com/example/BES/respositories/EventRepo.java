package com.example.BES.respositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.BES.models.Event;

@Repository
public interface EventRepo extends JpaRepository<Event, Long>{

}
