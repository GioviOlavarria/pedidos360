import { Pipe, PipeTransform } from '@angular/core';
import { formatMoney } from '../core/format';

/** {{ total | money }} → $8.970 */
@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(value: number | string | null | undefined): string {
    return formatMoney(value);
  }
}
