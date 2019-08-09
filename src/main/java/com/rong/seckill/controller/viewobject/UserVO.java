package com.rong.seckill.controller.viewobject;

import lombok.Data;

/**
 * @Author chenrong
 * @Date 2019-08-11 15:27
 **/
@Data
public class UserVO {
    private Integer id;
    private String name;
    private Byte gender;
    private Integer age;
    private String telphone;
}
