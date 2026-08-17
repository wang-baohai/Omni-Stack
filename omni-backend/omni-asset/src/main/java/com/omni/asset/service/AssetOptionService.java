package com.omni.asset.service;

import com.omni.asset.dto.AssetViews;

import java.util.List;

/** 资产操作候选项服务。 */
public interface AssetOptionService {

    /**
     * 查询当前租户内启用的用户候选。
     *
     * @param keyword 用户名或昵称关键词
     * @param limit 最大数量
     * @return 用户候选
     */
    List<AssetViews.UserOptionVO> listUsers(String keyword, int limit);

    /**
     * 查询当前数据范围内可发起资产操作的资产候选。
     *
     * @param keyword 资产编号或名称关键词
     * @param limit 最大数量
     * @return 资产候选
     */
    List<AssetViews.AssetOptionVO> listEligibleAssets(String keyword, int limit);

    /**
     * 查询当前租户已批准的供应商候选。
     *
     * @param keyword 供应商名称或编号关键词
     * @param limit 最大数量
     * @return 供应商候选
     */
    List<AssetViews.SupplierOptionVO> listSuppliers(String keyword, int limit);
}
