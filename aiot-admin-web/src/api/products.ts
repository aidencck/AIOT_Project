import http from '@/api/http';
import type { ProductReq, ProductResp, ProductThingModelUpdateReq } from '@/types/products';

export function getProducts() {
  return http.get<ProductResp[], ProductResp[]>('/api/v1/products');
}

export function createProduct(data: ProductReq) {
  return http.post<string, string>('/api/v1/products', data);
}

export function deleteProduct(productKey: string) {
  return http.delete<void, void>(`/api/v1/products/${productKey}`);
}

export function updateProductThingModel(productKey: string, data: ProductThingModelUpdateReq) {
  return http.put<void, void>(`/api/v1/products/${productKey}/thing-model`, data.thingModelJson, {
    headers: { 'Content-Type': 'text/plain' }
  });
}
