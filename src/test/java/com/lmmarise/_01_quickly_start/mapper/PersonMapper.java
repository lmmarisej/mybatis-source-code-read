package com.lmmarise._01_quickly_start.mapper;

import com.lmmarise._01_quickly_start.pojo.Person;

public interface PersonMapper {

    Person selectById(Long id);

}
