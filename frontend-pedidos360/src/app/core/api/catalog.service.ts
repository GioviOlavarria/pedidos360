import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Product, ProductRequest, ProductSales } from '../models';

/** Productos e inventario (ms-pedidos360-catalog a través del BFF). */
@Injectable({ providedIn: 'root' })
export class CatalogService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.bffUrl}/api/catalog`;

  getProducts(): Observable<Product[]> {
    return this.http.get<Product[]>(this.base);
  }

  getProduct(id: number): Observable<Product> {
    return this.http.get<Product>(`${this.base}/${id}`);
  }

  createProduct(data: ProductRequest): Observable<Product> {
    return this.http.post<Product>(this.base, data);
  }

  updateProduct(id: number, data: ProductRequest): Observable<Product> {
    return this.http.put<Product>(`${this.base}/${id}`, data);
  }

  deleteProduct(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  /** Productos más vendidos (solo Admin): catalog lo calcula con los eventos de pedidos de Kafka. */
  getSalesStats(): Observable<ProductSales[]> {
    return this.http.get<ProductSales[]>(`${this.base}/sales-stats`);
  }
}
