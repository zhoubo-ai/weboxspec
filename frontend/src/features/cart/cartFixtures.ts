import type { MenuDish } from '../../shared/api/types';
export const pasta: MenuDish = {
  dishId: '10000000-0000-4000-8000-000000000001',name: 'Tomato Pasta',description: 'Pasta with tomato sauce.',imageUrl: '/media/10000000-0000-4000-8000-000000000002',basePriceCents: 2650,category: 'Western',protein: 'None',allergens: ['Gluten'],spiceLevel: 'None',dishVersion: 1,remaining: 20,inventoryVersion: 0,preferenceMatch: { cuisine: false, spice: false },
  optionGroups: [{ groupId: '10000000-0000-4000-8000-000000000003',name: 'Pasta type',required: true,selectionMode: 'Single',options: [{ optionId: '10000000-0000-4000-8000-000000000004',name: 'Spaghetti',extraCents: 0,allergens: ['Gluten'] },{ optionId: '10000000-0000-4000-8000-000000000005',name: 'Fusilli',extraCents: 0,allergens: ['Gluten'] }] },{ groupId: '10000000-0000-4000-8000-000000000006',name: 'Add-ons',required: false,selectionMode: 'Multiple',options: [{ optionId: '10000000-0000-4000-8000-000000000007',name: 'Cheese',extraCents: 300,allergens: ['Dairy'] }] }],
};
export const spaghetti = pasta.optionGroups[0].options[0].optionId;
export const fusilli = pasta.optionGroups[0].options[1].optionId;
export const cheese = pasta.optionGroups[1].options[0].optionId;
