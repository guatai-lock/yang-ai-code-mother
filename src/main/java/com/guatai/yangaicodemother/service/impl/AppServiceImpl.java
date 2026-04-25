package com.guatai.yangaicodemother.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.guatai.yangaicodemother.model.entity.App;
import com.guatai.yangaicodemother.mapper.AppMapper;
import com.guatai.yangaicodemother.service.AppService;
import org.springframework.stereotype.Service;

/**
 * 应用 服务层实现。
 *
 */
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App>  implements AppService{

}
