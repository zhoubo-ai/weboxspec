-- Reporting fixture: D is the explicit historical query day, T = D + 7 is business today.
-- Order numbers below correspond to independent handwritten expected-metrics.json.
INSERT INTO orders(id,user_id,quote_id,idempotency_key,request_hash,delivery_date,meal,delivery_address_snapshot,status,total_quantity,total_cents,version,created_at,confirmed_at,completed_at,cancelled_at,updated_at)
SELECT id,user_id,UUID(),UUID(),REPEAT('0',64),delivery_date,meal,'Reporting fixture office',status,quantity,amount,0,created_at,
 CASE WHEN status IN ('Confirmed','Completed') THEN DATE_ADD(created_at,INTERVAL 5 MINUTE) ELSE NULL END,
 CASE WHEN status='Completed' THEN DATE_ADD(created_at,INTERVAL 10 MINUTE) ELSE NULL END,
 CASE WHEN status='Cancelled' THEN DATE_ADD(created_at,INTERVAL 5 MINUTE) ELSE NULL END,
 CASE WHEN status='Pending' THEN created_at ELSE DATE_ADD(created_at,INTERVAL 10 MINUTE) END
FROM (
 SELECT :o1 AS id,:u1 AS user_id,DATE_ADD(:D, INTERVAL -6 DAY) AS delivery_date,'Completed' AS status,'Lunch' AS meal,2 AS quantity,3000 AS amount,TIMESTAMP(DATE_ADD(:D, INTERVAL -7 DAY),'08:00:00') AS created_at
 UNION ALL
 SELECT :o2,:u1,DATE_ADD(:D, INTERVAL -4 DAY),'Pending','Dinner',1,2000,TIMESTAMP(DATE_ADD(:D, INTERVAL -5 DAY),'08:00:00')
 UNION ALL
 SELECT :o3,:u1,DATE_ADD(:D, INTERVAL -3 DAY),'Cancelled','Lunch',1,1000,TIMESTAMP(DATE_ADD(:D, INTERVAL -4 DAY),'08:00:00')
 UNION ALL
 SELECT :o4,:u1,DATE_ADD(:D, INTERVAL -2 DAY),'Completed','Dinner',1,2000,TIMESTAMP(DATE_ADD(:D, INTERVAL -3 DAY),'08:00:00')
 UNION ALL
 SELECT :o5,:u1,DATE_ADD(:D, INTERVAL -1 DAY),'Confirmed','Lunch',1,1000,TIMESTAMP(DATE_ADD(:D, INTERVAL -2 DAY),'08:00:00')
 UNION ALL
 SELECT :o6,:u1,DATE_ADD(:D, INTERVAL 0 DAY),'Completed','Lunch',2,2500,TIMESTAMP(DATE_ADD(:D, INTERVAL -1 DAY),'08:00:00')
 UNION ALL
 SELECT :o7,:u2,DATE_ADD(:D, INTERVAL 0 DAY),'Completed','Dinner',2,4000,TIMESTAMP(DATE_ADD(:D, INTERVAL -1 DAY),'08:00:00')
 UNION ALL
 SELECT :o8,:u1,DATE_ADD(:D, INTERVAL 0 DAY),'Completed','Lunch',2,3000,TIMESTAMP(DATE_ADD(:D, INTERVAL -1 DAY),'09:00:00')
 UNION ALL
 SELECT :o9,:u1,DATE_ADD(:D, INTERVAL 0 DAY),'Pending','Lunch',1,1800,TIMESTAMP(DATE_ADD(:D, INTERVAL -1 DAY),'08:00:00')
 UNION ALL
 SELECT :o10,:u3,DATE_ADD(:D, INTERVAL 0 DAY),'Cancelled','Dinner',1,1800,TIMESTAMP(DATE_ADD(:D, INTERVAL -1 DAY),'08:00:00')
 UNION ALL
 SELECT :o11,:u2,DATE_ADD(:D, INTERVAL 0 DAY),'Confirmed','Dinner',1,1800,TIMESTAMP(DATE_ADD(:D, INTERVAL -1 DAY),'08:00:00')
 UNION ALL
 SELECT :o12,:u1,DATE_ADD(:D, INTERVAL 1 DAY),'Completed','Lunch',1,1000,TIMESTAMP(DATE_ADD(:D, INTERVAL 0 DAY),'08:00:00')
 UNION ALL
 SELECT :o13,:u1,DATE_ADD(:D, INTERVAL 3 DAY),'Cancelled','Dinner',1,1000,TIMESTAMP(DATE_ADD(:D, INTERVAL 2 DAY),'08:00:00')
 UNION ALL
 SELECT :o14,:u1,DATE_ADD(:D, INTERVAL 4 DAY),'Completed','Lunch',2,4600,TIMESTAMP(DATE_ADD(:D, INTERVAL 3 DAY),'08:00:00')
 UNION ALL
 SELECT :o15,:u1,DATE_ADD(:D, INTERVAL 5 DAY),'Pending','Lunch',1,2000,TIMESTAMP(DATE_ADD(:D, INTERVAL 4 DAY),'08:00:00')
 UNION ALL
 SELECT :o16,:u1,DATE_ADD(:D, INTERVAL 7 DAY),'Completed','Dinner',1,1800,TIMESTAMP(DATE_ADD(:D, INTERVAL 6 DAY),'08:00:00')
 UNION ALL
 SELECT :o17,:u2,DATE_ADD(:D, INTERVAL 7 DAY),'Confirmed','Lunch',1,1000,TIMESTAMP(DATE_ADD(:D, INTERVAL 6 DAY),'08:00:00')
 UNION ALL
 SELECT :o18,:u1,DATE_ADD(:D, INTERVAL 7 DAY),'Cancelled','Lunch',1,2000,TIMESTAMP(DATE_ADD(:D, INTERVAL 6 DAY),'08:00:00')
) source;

INSERT INTO order_items(id,order_id,dish_id,dish_name_snapshot,dish_version_snapshot,configuration_key,options_snapshot,effective_allergens_snapshot,base_price_cents,unit_price_cents,quantity,subtotal_cents) VALUES
 (UUID(),:o1,:A,'Old Burger',1,SHA2('500',256),JSON_ARRAY(JSON_OBJECT('optionId',:option,'groupId',:groupId,'groupName','Extras','optionName','Cheese','extraCents',500,'allergens',JSON_ARRAY('Dairy'))),JSON_ARRAY('Dairy'),1000,1500,2,3000),
 (UUID(),:o2,:B,'Earlier Pasta',1,SHA2('0',256),JSON_ARRAY(),JSON_ARRAY(),2000,2000,1,2000),
 (UUID(),:o3,:A,'Old Burger',1,SHA2('0',256),JSON_ARRAY(),JSON_ARRAY(),1000,1000,1,1000),
 (UUID(),:o4,:B,'Earlier Pasta',1,SHA2('0',256),JSON_ARRAY(),JSON_ARRAY(),2000,2000,1,2000),
 (UUID(),:o5,:A,'Old Burger',1,SHA2('0',256),JSON_ARRAY(),JSON_ARRAY(),1000,1000,1,1000),
 (UUID(),:o6,:A,'Original Burger',1,SHA2('0',256),JSON_ARRAY(),JSON_ARRAY(),1000,1000,1,1000),
 (UUID(),:o6,:A,'Original Burger',1,SHA2('500',256),JSON_ARRAY(JSON_OBJECT('optionId',:option,'groupId',:groupId,'groupName','Extras','optionName','Cheese','extraCents',500,'allergens',JSON_ARRAY('Dairy'))),JSON_ARRAY('Dairy'),1000,1500,1,1500),
 (UUID(),:o7,:B,'Earlier Pasta',1,SHA2('0',256),JSON_ARRAY(),JSON_ARRAY(),2000,2000,2,4000),
 (UUID(),:o8,:A,'Latest Burger',1,SHA2('0',256),JSON_ARRAY(),JSON_ARRAY(),1000,1000,1,1000),
 (UUID(),:o8,:B,'Latest Pasta',1,SHA2('0',256),JSON_ARRAY(),JSON_ARRAY(),2000,2000,1,2000),
 (UUID(),:o9,:C,'Garden Greens',1,SHA2('0',256),JSON_ARRAY(),JSON_ARRAY(),1800,1800,1,1800),
 (UUID(),:o10,:J,'Japanese Greens',1,SHA2('0',256),JSON_ARRAY(),JSON_ARRAY(),1800,1800,1,1800),
 (UUID(),:o11,:C,'Garden Greens',1,SHA2('0',256),JSON_ARRAY(),JSON_ARRAY(),1800,1800,1,1800),
 (UUID(),:o12,:A,'Latest Burger',1,SHA2('0',256),JSON_ARRAY(),JSON_ARRAY(),1000,1000,1,1000),
 (UUID(),:o13,:A,'Latest Burger',1,SHA2('0',256),JSON_ARRAY(),JSON_ARRAY(),1000,1000,1,1000),
 (UUID(),:o14,:B,'Latest Pasta',1,SHA2('300',256),JSON_ARRAY(JSON_OBJECT('optionId',:option,'groupId',:groupId,'groupName','Extras','optionName','Cheese','extraCents',300,'allergens',JSON_ARRAY('Dairy'))),JSON_ARRAY('Dairy'),2000,2300,2,4600),
 (UUID(),:o15,:B,'Latest Pasta',1,SHA2('0',256),JSON_ARRAY(),JSON_ARRAY(),2000,2000,1,2000),
 (UUID(),:o16,:C,'Fresh Greens',1,SHA2('0',256),JSON_ARRAY(),JSON_ARRAY(),1800,1800,1,1800),
 (UUID(),:o17,:A,'Latest Burger',1,SHA2('0',256),JSON_ARRAY(),JSON_ARRAY(),1000,1000,1,1000),
 (UUID(),:o18,:B,'Latest Pasta',1,SHA2('0',256),JSON_ARRAY(),JSON_ARRAY(),2000,2000,1,2000);
