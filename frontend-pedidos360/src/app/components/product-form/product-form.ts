import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CatalogService } from '../../core/api/catalog.service';
import { Product, ProductRequest } from '../../core/models';

/** Modal del Admin para crear o editar un producto. Emite `closed(true)` si guardó cambios. */
@Component({
  selector: 'app-product-form',
  imports: [ReactiveFormsModule],
  templateUrl: './product-form.html',
})
export class ProductForm implements OnInit {
  private readonly catalog = inject(CatalogService);

  readonly product = input<Product | null>(null);
  readonly closed = output<boolean>();

  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    description: ['', Validators.required],
    price: [0, [Validators.required, Validators.min(0)]],
    stock: [0, [Validators.required, Validators.min(0)]],
  });
  protected readonly saving = signal(false);
  protected readonly error = signal('');

  ngOnInit(): void {
    const product = this.product();
    if (product) {
      this.form.setValue({
        name: product.name,
        description: product.description ?? '',
        price: product.price,
        stock: product.stock,
      });
    }
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const payload: ProductRequest = {
      name: value.name.trim(),
      description: value.description.trim(),
      price: Number(value.price),
      stock: Math.trunc(Number(value.stock)),
    };
    const product = this.product();
    const request = product ? this.catalog.updateProduct(product.id, payload) : this.catalog.createProduct(payload);

    this.saving.set(true);
    this.error.set('');
    request.subscribe({
      next: () => this.closed.emit(true),
      error: () => {
        this.error.set('No se pudo guardar el producto. Intente nuevamente.');
        this.saving.set(false);
      },
    });
  }
}
