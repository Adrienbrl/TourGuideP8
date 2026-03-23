package com.openclassrooms.tourguide.service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

@Service
public class RewardsService {
    private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;
	private static final int THREAD_POOL_SIZE = 100;
	private static final Executor REWARD_EXECUTOR = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

	// proximity in miles
    private int defaultProximityBuffer = 10;
	private int proximityBuffer = defaultProximityBuffer;
	private int attractionProximityRange = 200;
	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;
	private final List<Attraction> attractions;
	
	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
		this.attractions = List.copyOf(gpsUtil.getAttractions());
	}
	
	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}
	
	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}
	
	public void calculateRewards(User user) {
		calculateRewards(user, List.copyOf(user.getVisitedLocations()));
	}

	public void calculateRewards(User user, VisitedLocation visitedLocation) {
		calculateRewards(user, List.of(visitedLocation));
	}

	public void calculateRewards(List<User> users) {
		int chunkSize = Math.max(1, (int) Math.ceil((double) users.size() / THREAD_POOL_SIZE));
		CompletableFuture.allOf(java.util.stream.IntStream.iterate(0, start -> start < users.size(), start -> start + chunkSize)
				.mapToObj(start -> users.subList(start, Math.min(start + chunkSize, users.size())))
				.map(chunk -> CompletableFuture.runAsync(() -> chunk.forEach(this::calculateRewards), REWARD_EXECUTOR))
				.toArray(CompletableFuture[]::new))
			.join();
	}

	public void calculateRewardsForLatestLocation(List<User> users) {
		int chunkSize = Math.max(1, (int) Math.ceil((double) users.size() / THREAD_POOL_SIZE));
		CompletableFuture.allOf(java.util.stream.IntStream.iterate(0, start -> start < users.size(), start -> start + chunkSize)
				.mapToObj(start -> users.subList(start, Math.min(start + chunkSize, users.size())))
				.map(chunk -> CompletableFuture.runAsync(() -> chunk.forEach(user -> calculateRewards(user, user.getLastVisitedLocation())), REWARD_EXECUTOR))
				.toArray(CompletableFuture[]::new))
			.join();
	}

	private void calculateRewards(User user, List<VisitedLocation> userLocations) {
		Set<String> rewardedAttractionNames = user.getUserRewards().stream()
				.map(reward -> reward.attraction.attractionName)
				.collect(Collectors.toSet());
		
		for(VisitedLocation visitedLocation : userLocations) {
			for(Attraction attraction : attractions) {
				if (rewardedAttractionNames.contains(attraction.attractionName)) {
					continue;
				}
				if (nearAttraction(visitedLocation, attraction)) {
					user.addUserReward(new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user)));
					rewardedAttractionNames.add(attraction.attractionName);
				}
			}
		}
	}
	
	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return getDistance(attraction, location) > attractionProximityRange ? false : true;
	}
	
	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return getDistance(attraction, visitedLocation.location) > proximityBuffer ? false : true;
	}
	
	private int getRewardPoints(Attraction attraction, User user) {
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
	}

	public int getAttractionRewardPoints(Attraction attraction, User user) {
		return getRewardPoints(attraction, user);
	}
	
	public double getDistance(Location loc1, Location loc2) {
        double lat1 = Math.toRadians(loc1.latitude);
        double lon1 = Math.toRadians(loc1.longitude);
        double lat2 = Math.toRadians(loc2.latitude);
        double lon2 = Math.toRadians(loc2.longitude);

        double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2)
                               + Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

        double nauticalMiles = 60 * Math.toDegrees(angle);
        double statuteMiles = STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
        return statuteMiles;
	}

}
