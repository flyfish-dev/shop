package group.flyfish.dev.shop.service.support.h5zhifu.convert;

import group.flyfish.dev.shop.domain.dto.ShopOrderDto;
import group.flyfish.dev.shop.service.support.dto.ShopOrderPayResultDto;
import group.flyfish.dev.shop.service.support.h5zhifu.bean.H5ZhiFuPayDto;
import group.flyfish.dev.shop.service.support.h5zhifu.bean.H5ZhiFuPayResultDto;
import org.mapstruct.Mapper;

@Mapper
public interface H5ZhiFuConvert {

    /**
     * 转换支付实体
     *
     * @param pay 支付信息
     * @return 结果
     */
    H5ZhiFuPayDto convert(ShopOrderDto pay);

    ShopOrderPayResultDto convert(H5ZhiFuPayResultDto result);
}
