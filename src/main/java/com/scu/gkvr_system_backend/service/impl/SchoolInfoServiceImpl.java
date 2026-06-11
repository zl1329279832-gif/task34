package com.scu.gkvr_system_backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scu.gkvr_system_backend.mapper.SchoolInfoMapper;
import com.scu.gkvr_system_backend.pojo.SchoolInfo;
import com.scu.gkvr_system_backend.service.SchoolInfoService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Liyang
 * @description 针对表【school_info】的数据库操作Service实现
 * @createDate 2024-03-23 16:46:11
 */
@Service
public class SchoolInfoServiceImpl extends ServiceImpl<SchoolInfoMapper, SchoolInfo>
        implements SchoolInfoService {

    @Override
    public Map<String, Object> getAllSchools(int page) {
        Map<String, Object> result = new HashMap<>();
        Page<SchoolInfo> schoolInfoPage = this.baseMapper.selectPage(new Page<>(page, 10), null);
        result.put("schools", schoolInfoPage.getRecords());
        result.put("total", schoolInfoPage.getTotal());
        return result;
    }

    @Override
    public Map<String, Object> get985Schools(int page) {
        Map<String, Object> result = new HashMap<>();
        LambdaQueryWrapper<SchoolInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SchoolInfo::getIs985, "985");
        Page<SchoolInfo> schoolInfoPage = this.baseMapper.selectPage(new Page<>(page, 10), wrapper);
        result.put("schools", schoolInfoPage.getRecords());
        result.put("total", schoolInfoPage.getTotal());
        return result;
    }

    @Override
    public Map<String, Object> get211Schools(int page) {
        Map<String, Object> result = new HashMap<>();
        LambdaQueryWrapper<SchoolInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SchoolInfo::getIs211, "211");
        Page<SchoolInfo> schoolInfoPage = this.baseMapper.selectPage(new Page<>(page, 10), wrapper);
        result.put("schools", schoolInfoPage.getRecords());
        result.put("total", schoolInfoPage.getTotal());
        return result;
    }

    @Override
    public Map<String, Object> getDoublehighSchools(int page) {
        Map<String, Object> result = new HashMap<>();
        LambdaQueryWrapper<SchoolInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SchoolInfo::getDoublehigh, "双一流");
        Page<SchoolInfo> schoolInfoPage = this.baseMapper.selectPage(new Page<>(page, 10), wrapper);
        result.put("schools", schoolInfoPage.getRecords());
        result.put("total", schoolInfoPage.getTotal());
        return result;
    }

    @Override
    public Map<String, Object> getByProvince(int page, String provinceName) {
        Map<String, Object> result = new HashMap<>();
        LambdaQueryWrapper<SchoolInfo> wrapper = new LambdaQueryWrapper<>();
        if (provinceName.equals("全部")) {
            return getAllSchools(page);
        }
        wrapper.eq(SchoolInfo::getProvinceName, provinceName);
        Page<SchoolInfo> schoolInfoPage = this.baseMapper.selectPage(new Page<>(page, 10), wrapper);
        result.put("schools", schoolInfoPage.getRecords());
        result.put("total", schoolInfoPage.getTotal());
        return result;
    }

    @Override
    public Map<String, Object> SearchByName(int page, String schoolName) {
        Map<String, Object> result = new HashMap<>();
        LambdaQueryWrapper<SchoolInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(SchoolInfo::getSchoolName, schoolName);
        Page<SchoolInfo> schoolInfoPage = this.baseMapper.selectPage(new Page<>(page, 10), wrapper);
        result.put("schools", schoolInfoPage.getRecords());
        result.put("total", schoolInfoPage.getTotal());
        return result;
    }

}




