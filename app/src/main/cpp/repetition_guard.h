#pragma once
#include <vector>
#include <set>
#include <cstdint>
#include <cstddef>
// Only exact generated-token loops; prompt tokens never count as repetition.
struct RepetitionGuard {
    std::vector<int32_t> ids;std::vector<size_t> byte_ends;size_t keep_bytes=0;
    bool push(int32_t id,size_t bytes){
        ids.push_back(id);byte_ends.push_back(bytes);const size_t n=ids.size();
        if(n>=32){bool same=true;for(size_t i=n-31;i<n;i++)same &= ids[i]==ids[n-32];if(same){keep_bytes=byte_ends[n-32];return true;}}
        for(size_t period=12;period<=96 && 3*period<=n;++period){
            bool same=true;for(size_t i=n-2*period;i<n;++i)if(ids[i]!=ids[i-period]){same=false;break;}
            if(!same)continue;
            std::set<int32_t> distinct(ids.begin()+n-period,ids.end());if(distinct.size()<5)continue;
            keep_bytes=byte_ends[n-2*period-1];return true;
        }
        return false;
    }
};
