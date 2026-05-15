package com.lynx.simulation.repository;

import com.lynx.simulation.model.OptionContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OptionRepository extends JpaRepository<OptionContract, String> {
}
