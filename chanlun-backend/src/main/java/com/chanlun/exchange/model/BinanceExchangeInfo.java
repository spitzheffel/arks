package com.chanlun.exchange.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 币安 exchangeInfo 响应模型
 * 
 * 对应币安 API: GET /api/v3/exchangeInfo (现货)
 *              GET /fapi/v1/exchangeInfo (U本位合约)
 *              GET /dapi/v1/exchangeInfo (币本位合约)
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BinanceExchangeInfo {

    /**
     * 时区
     */
    private String timezone;

    /**
     * 服务器时间
     */
    private Long serverTime;

    /**
     * 交易对列表
     */
    private List<BinanceSymbol> symbols;

    /**
     * 币安交易对信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BinanceSymbol {

        /**
         * 交易对代码 (如 BTCUSDT)
         */
        private String symbol;

        /**
         * 交易对状态 (TRADING, HALT, BREAK 等)
         */
        private String status;

        /**
         * 基础货币 (如 BTC)
         */
        private String baseAsset;

        /**
         * 基础货币精度
         */
        private Integer baseAssetPrecision;

        /**
         * 报价货币 (如 USDT)
         */
        private String quoteAsset;

        /**
         * 报价货币精度
         */
        private Integer quotePrecision;

        /**
         * 报价货币精度（合约使用）
         */
        private Integer quoteAssetPrecision;

        /**
         * 价格精度（合约使用）
         */
        private Integer pricePrecision;

        /**
         * 数量精度（合约使用）
         */
        private Integer quantityPrecision;

        /**
         * 合约类型（合约使用）
         * PERPETUAL: 永续合约
         * CURRENT_QUARTER: 当季合约
         * NEXT_QUARTER: 次季合约
         */
        private String contractType;

        /**
         * 获取价格精度
         * 现货使用 quotePrecision，合约使用 pricePrecision
         */
        public Integer getEffectivePricePrecision() {
            if (pricePrecision != null) {
                return pricePrecision;
            }
            return quotePrecision != null ? quotePrecision : 8;
        }

        /**
         * 获取数量精度
         * 现货使用 baseAssetPrecision，合约使用 quantityPrecision
         */
        public Integer getEffectiveQuantityPrecision() {
            if (quantityPrecision != null) {
                return quantityPrecision;
            }
            return baseAssetPrecision != null ? baseAssetPrecision : 8;
        }

        /**
         * 判断是否为交易中状态
         */
        public boolean isTrading() {
            return "TRADING".equalsIgnoreCase(status);
        }
    }
}
