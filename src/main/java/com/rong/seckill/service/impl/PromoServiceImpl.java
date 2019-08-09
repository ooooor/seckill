package com.rong.seckill.service.impl;

import com.rong.seckill.dao.PromoDOMapper;
import com.rong.seckill.dataobject.Promo;
import com.rong.seckill.service.ItemService;
import com.rong.seckill.service.PromoService;
import com.rong.seckill.service.UserService;
import com.rong.seckill.service.model.ItemModel;
import com.rong.seckill.service.model.PromoModel;
import com.rong.seckill.service.model.UserModel;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @Author chenrong
 * @Date 2019-08-11 15:27
 **/
@Service
public class PromoServiceImpl implements PromoService {

    @Autowired
    private PromoDOMapper promoDOMapper;

    @Autowired
    private ItemService itemService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private UserService userService;

    @Override
    public PromoModel getPromoByItemId(Integer itemId) {
        //获取对应商品的秒杀活动信息
        Promo promo = promoDOMapper.selectByItemId(itemId);

        //dataobject->model
        PromoModel promoModel = convertFromDataObject(promo);
        if(promoModel == null){
            return null;
        }

        //判断当前时间是否秒杀活动即将开始或正在进行
        if(promoModel.getStartDate().isAfterNow()){
            promoModel.setStatus(1);
        }else if(promoModel.getEndDate().isBeforeNow()){
            promoModel.setStatus(3);
        }else{
            promoModel.setStatus(2);
        }
        return promoModel;
    }

    @Override
    public void publishPromo(Integer promoId) {
        //通过活动id获取活动
        Promo promo = promoDOMapper.selectByPrimaryKey(promoId);
        if(promo.getItemId() == null || promo.getItemId().intValue() == 0){
            return;
        }
        ItemModel itemModel = itemService.getItemById(promo.getItemId());

        //将库存同步到redis内
        redisTemplate.opsForValue().set("promo_item_stock_"+itemModel.getId(), itemModel.getStock());

        //将大闸的限制数字设到redis内
        redisTemplate.opsForValue().set("promo_door_count_"+promoId,itemModel.getStock().intValue() * 5);

    }

    @Override
    public String generateSecondKillToken(Integer promoId,Integer itemId,Integer userId) {

        //判断是否库存已售罄，若对应的售罄key存在，则直接返回下单失败
        if(redisTemplate.hasKey("promo_item_stock_invalid_"+itemId)){
            return null;
        }
        Promo promo = promoDOMapper.selectByPrimaryKey(promoId);

        //dataobject->model
        PromoModel promoModel = convertFromDataObject(promo);
        if(promoModel == null){
            return null;
        }

        //判断当前时间是否秒杀活动即将开始或正在进行
        if(promoModel.getStartDate().isAfterNow()){
            promoModel.setStatus(1);
        }else if(promoModel.getEndDate().isBeforeNow()){
            promoModel.setStatus(3);
        }else{
            promoModel.setStatus(2);
        }
        //判断活动是否正在进行
        if(promoModel.getStatus().intValue() != 2){
            return null;
        }
        //判断item信息是否存在
        ItemModel itemModel = itemService.getItemByIdInCache(itemId);
        if(itemModel == null){
            return null;
        }
        //判断用户信息是否存在
        UserModel userModel = userService.getUserByIdInCache(userId);
        if(userModel == null){
            return null;
        }

        //获取秒杀大闸的count数量
        long result = redisTemplate.opsForValue().increment("promo_door_count_"+promoId,-1);
        if(result < 0){
            return null;
        }
        //生成token并且存入redis内并给一个5分钟的有效期
        String token = UUID.randomUUID().toString().replace("-","");

        redisTemplate.opsForValue().set("promo_token_"+promoId+"_userid_"+userId+"_itemid_"+itemId,token);
        redisTemplate.expire("promo_token_"+promoId+"_userid_"+userId+"_itemid_"+itemId,5, TimeUnit.MINUTES);

        return token;
    }

    private PromoModel convertFromDataObject(Promo promo){
        if(promo == null){
            return null;
        }
        PromoModel promoModel = new PromoModel();
        BeanUtils.copyProperties(promo,promoModel);
        promoModel.setPromoItemPrice(new BigDecimal(promo.getPromoItemPrice()));
        promoModel.setStartDate(new DateTime(promo.getStartDate()));
        promoModel.setEndDate(new DateTime(promo.getEndDate()));
        return promoModel;
    }
}
