package com.example.moviebooking.repository;

import com.example.moviebooking.entity.City;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CityRepository extends JpaRepository<City, Long> {
}
