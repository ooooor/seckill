package com.rong.seckill.service.impl;

import com.rong.seckill.dao.UserDOMapper;
import com.rong.seckill.dataobject.User;
import com.rong.seckill.dataobject.UserPassword;
import com.rong.seckill.error.BusinessException;
import com.rong.seckill.error.EmBusinessError;
import com.rong.seckill.service.UserService;
import com.rong.seckill.service.model.UserModel;
import com.rong.seckill.validator.ValidatorImpl;
import com.rong.seckill.validator.ValidationResult;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.rong.seckill.dao.UserPasswordDOMapper;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * @Author chenrong
 * @Date 2019-08-11 15:27
 **/
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserDOMapper userDOMapper;

    @Autowired
    private UserPasswordDOMapper userPasswordDOMapper;

    @Autowired
    private ValidatorImpl validator;


    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public UserModel getUserById(Integer id) {
        //调用userdomapper获取到对应的用户dataobject
        User user = userDOMapper.selectByPrimaryKey(id);
        if(user == null){
            return null;
        }
        //通过用户id获取对应的用户加密密码信息
        UserPassword userPassword = userPasswordDOMapper.selectByUserId(user.getId());

        return convertFromDataObject(user, userPassword);
    }

    @Override
    public UserModel getUserByIdInCache(Integer id) {
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get("user_validate_"+id);
        if(userModel == null){
            userModel = this.getUserById(id);
            redisTemplate.opsForValue().set("user_validate_"+id,userModel);
            redisTemplate.expire("user_validate_"+id,10, TimeUnit.MINUTES);
        }
        return userModel;
    }

    @Override
    @Transactional
    public void register(UserModel userModel) throws BusinessException {
        if(userModel == null){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR);
        }
        ValidationResult result =  validator.validate(userModel);
        if(result.isHasErrors()){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,result.getErrMsg());
        }



        //实现model->dataobject方法
        User user = convertFromModel(userModel);
        try{
            userDOMapper.insertSelective(user);
        }catch(DuplicateKeyException ex){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"手机号已重复注册");
        }



        userModel.setId(user.getId());

        UserPassword userPassword = convertPasswordFromModel(userModel);
        userPasswordDOMapper.insertSelective(userPassword);

        return;
    }

    @Override
    public UserModel validateLogin(String telphone, String encrptPassword) throws BusinessException {
        //通过用户的手机获取用户信息
        User user = userDOMapper.selectByTelphone(telphone);
        if(user == null){
            throw new BusinessException(EmBusinessError.USER_LOGIN_FAIL);
        }
        UserPassword userPassword = userPasswordDOMapper.selectByUserId(user.getId());
        UserModel userModel = convertFromDataObject(user, userPassword);

        //比对用户信息内加密的密码是否和传输进来的密码相匹配
        if(!StringUtils.equals(encrptPassword,userModel.getEncrptPassword())){
            throw new BusinessException(EmBusinessError.USER_LOGIN_FAIL);
        }
        return userModel;
    }


    private UserPassword convertPasswordFromModel(UserModel userModel){
        if(userModel == null){
            return null;
        }
        UserPassword userPassword = new UserPassword();
        userPassword.setEncrptPassword(userModel.getEncrptPassword());
        userPassword.setUserId(userModel.getId());
        return userPassword;
    }
    private User convertFromModel(UserModel userModel){
        if(userModel == null){
            return null;
        }
        User user = new User();
        BeanUtils.copyProperties(userModel, user);

        return user;
    }
    private UserModel convertFromDataObject(User user, UserPassword userPassword){
        if(user == null){
            return null;
        }
        UserModel userModel = new UserModel();
        BeanUtils.copyProperties(user,userModel);

        if(userPassword != null){
            userModel.setEncrptPassword(userPassword.getEncrptPassword());
        }

        return userModel;
    }
}
