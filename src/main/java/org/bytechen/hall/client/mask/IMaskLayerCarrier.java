package org.bytechen.hall.client.mask;

import org.bytechen.hall.api.mask.MaskLayerSpec;

import java.util.List;

/**
 * 由 <b>BakedModel</b> 携带 mask 层的接口 —— 走模型 JSON 那条路的载体。
 *
 * <p>实现者有两处（都在本包里）：</p>
 * <ul>
 *   <li>{@code BakedModelCosmic} —— 用 {@code "loader": "hall:cosmic"} 的物品，
 *       在原有 cosmic 块之外还可以在同一个 JSON 里追加 {@code "mask_layers"}；</li>
 *   <li>{@code BakedModelMaskLayers} —— 用 {@code "loader": "hall:mask_layers"}
 *       的物品，只要附加层、不要星空。</li>
 * </ul>
 *
 * <p>物品渲染管线只需要判一个 {@code instanceof IMaskLayerCarrier} 就能同时
 * 拿到两条路的层，不必分别识别两个具体的 BakedModel 类型。</p>
 */
public interface IMaskLayerCarrier {

    /** 模型 JSON 里声明的 mask 层（已解析、不可变）。没有则为空列表。 */
    List<MaskLayerSpec> maskLayers();
}
