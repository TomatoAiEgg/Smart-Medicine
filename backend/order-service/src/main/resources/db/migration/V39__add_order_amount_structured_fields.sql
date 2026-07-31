alter table order_main
    add column if not exists logistics_fee numeric(12, 2),
    add column if not exists discount_amount numeric(12, 2);

with amount_values as (
    select
        id,
        coalesce(
            raw_payload ->> 'logisticsFee',
            raw_payload ->> 'logistics_fee',
            raw_payload ->> 'freight',
            raw_payload ->> 'freightFee',
            raw_payload ->> 'freight_fee',
            raw_payload ->> 'shippingFee',
            raw_payload ->> 'shipping_fee',
            raw_payload ->> 'deliveryFee',
            raw_payload ->> 'delivery_fee',
            raw_payload ->> 'expressFee',
            raw_payload ->> 'express_fee'
        ) as logistics_fee_text,
        coalesce(
            raw_payload ->> 'discountAmount',
            raw_payload ->> 'discount_amount',
            raw_payload ->> 'discount',
            raw_payload ->> 'couponAmount',
            raw_payload ->> 'coupon_amount',
            raw_payload ->> 'preferentialAmount',
            raw_payload ->> 'preferential_amount',
            raw_payload ->> 'reduceAmount',
            raw_payload ->> 'reduce_amount',
            raw_payload ->> 'promotionAmount',
            raw_payload ->> 'promotion_amount'
        ) as discount_amount_text
    from order_main
)
update order_main o
set logistics_fee = coalesce(
        o.logistics_fee,
        case when trim(amount_values.logistics_fee_text) ~ '^-?[0-9]+([.][0-9]+)?$'
            then trim(amount_values.logistics_fee_text)::numeric
        end
    ),
    discount_amount = coalesce(
        o.discount_amount,
        case when trim(amount_values.discount_amount_text) ~ '^-?[0-9]+([.][0-9]+)?$'
            then trim(amount_values.discount_amount_text)::numeric
        end
    )
from amount_values
where amount_values.id = o.id
  and (o.logistics_fee is null or o.discount_amount is null);
