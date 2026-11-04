package com.planner.route_planner_api.feature.customers;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.planner.route_planner_api.common.error.ConflictException;
import com.planner.route_planner_api.common.error.ResourceNotFoundException;
import com.planner.route_planner_api.common.web.PageResponse;
import com.planner.route_planner_api.feature.companies.CompanyAccess;
import com.planner.route_planner_api.feature.companies.CompanyMember;
import com.planner.route_planner_api.feature.customers.dto.ItemRequest;
import com.planner.route_planner_api.feature.customers.dto.ItemResponse;
import com.planner.route_planner_api.feature.customers.dto.UpdateItemRequest;
import com.planner.route_planner_api.feature.orders.Order;
import com.planner.route_planner_api.feature.orders.OrderRepository;
import com.planner.route_planner_api.feature.orders.OrderStatus;

// The catalogue isn't personal data: any member can read it, only the owner changes it (same as vehicles)
@Service
public class ItemService {

    private final ItemRepository itemRepository;
    private final OrderRepository orderRepository;
    private final CompanyAccess companyAccess;

    public ItemService(ItemRepository itemRepository, OrderRepository orderRepository, CompanyAccess companyAccess){
        this.itemRepository = itemRepository;
        this.orderRepository = orderRepository;
        this.companyAccess = companyAccess;
    }

    @Transactional
    public ItemResponse createItem(Long userId, Long companyId, ItemRequest request){
        CompanyMember membership = companyAccess.requireMember(companyId, userId);
        companyAccess.requireOwner(membership);

        String sku = cleanSku(request.sku());
        if (sku != null && itemRepository.existsByCompanyIdAndSku(companyId, sku)) {
            throw new ConflictException("Another item of this company already uses SKU " + sku);    // 409
        }
        Item item = new Item(membership.getCompany(), sku, request.name().strip(), request.weightKg(), request.volumeM3());
        return ItemResponse.from(itemRepository.save(item));
    }

    @Transactional(readOnly = true)
    public PageResponse<ItemResponse> listItems(Long userId, Long companyId, int page, int size){
        companyAccess.requireMember(companyId, userId);
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("name", "id"));
        return PageResponse.from(itemRepository.findByCompanyId(companyId, pageRequest).map(ItemResponse::from));
    }

    @Transactional
    public ItemResponse updateItem(Long userId, Long itemId, UpdateItemRequest request){
        Item item = findItemForOwner(itemId, userId);

        // PATCH: only touch the fields the client sent
        if (request.name() != null) {
            item.rename(request.name().strip());
        }
        if (request.sku() != null) {
            String sku = cleanSku(request.sku());
            if (sku != null && itemRepository.existsByCompanyIdAndSkuAndIdNot(item.getCompany().getId(), sku, itemId)) {
                throw new ConflictException("Another item of this company already uses SKU " + sku);   // 409
            }
            item.changeSku(sku);
        }
        if (request.volumeM3() != null) {
            item.changeVolume(request.volumeM3());
        }
        // compareTo, not equals: BigDecimal.equals says 20.5 and 20.500 are different
        if (request.weightKg() != null && request.weightKg().compareTo(item.getWeightKg()) != 0) {
            item.changeWeight(request.weightKg());
            refreshPendingOrderWeights(item);
        }
        // No save() needed: Hibernate sees the changes and runs the UPDATEs when the transaction commits
        return ItemResponse.from(item);
    }

    // orders.total_weight_kg is a stored copy of the lines' weights, so it goes stale when a unit weight changes.
    // Pending orders get the new total (the next plan must use it). Planned and finished orders keep the
    // total they were planned and delivered with.
    private void refreshPendingOrderWeights(Item item){
        for (Order order : orderRepository.findDistinctByItemsItemIdAndStatus(item.getId(), OrderStatus.pending)) {
            order.recalculateTotalWeight();
        }
    }

    // Trimmed; "" or "   " means no SKU
    private static String cleanSku(String sku){
        return (sku == null || sku.isBlank()) ? null : sku.strip();
    }

    // Item exists AND the user owns its company; a non-member gets 404, so ids in other companies stay hidden
    private Item findItemForOwner(Long itemId, Long userId){
        Item item = itemRepository.findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("Item", itemId));
        CompanyMember membership = companyAccess.findActiveMember(item.getCompany().getId(), userId)
            .orElseThrow(() -> new ResourceNotFoundException("Item", itemId));
        companyAccess.requireOwner(membership);
        return item;
    }
}
