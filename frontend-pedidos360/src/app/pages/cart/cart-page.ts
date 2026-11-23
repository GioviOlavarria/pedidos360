import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { CartService } from '../../core/cart.service';
import { CheckoutService } from '../../core/checkout.service';
import { apiErrorMessage } from '../../core/format';
import { MoneyPipe } from '../../shared/money.pipe';

/** Carrito: revisar productos y cantidades, y pagar con Flow. */
@Component({
  selector: 'app-cart-page',
  imports: [RouterLink, MoneyPipe],
  templateUrl: './cart-page.html',
  styleUrl: './cart-page.css',
})
export class CartPage {
  private readonly checkout = inject(CheckoutService);
  private readonly router = inject(Router);
  protected readonly cart = inject(CartService);
  protected readonly auth = inject(AuthService);

  protected readonly paying = signal(false);
  protected readonly error = signal('');

  protected changeQuantity(productId: number, value: number): void {
    if (Number.isFinite(value)) this.cart.setQuantity(productId, Math.trunc(value));
  }

  protected pay(): void {
    if (!this.auth.isAuthenticated()) {
      void this.router.navigateByUrl('/login');
      return;
    }
    this.paying.set(true);
    this.error.set('');
    this.checkout.checkoutCart().subscribe({
      next: (url) => this.checkout.goToGateway(url),
      error: (err) => {
        this.paying.set(false);
        this.error.set(apiErrorMessage(err, 'No se pudo iniciar el pago. Intente nuevamente.'));
      },
    });
  }
}
